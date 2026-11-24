package com.company.depcheck.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.depcheck.common.constant.TaskStatus;
import com.company.depcheck.config.RetentionProperties;
import com.company.depcheck.config.ScanProperties;
import com.company.depcheck.config.UploadProperties;
import com.company.depcheck.mapper.ProjectMapper;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.Project;
import com.company.depcheck.model.entity.ScanTask;

/**
 * 磁盘清理的唯一入口：报告目录与解压目录。
 *
 * <p>为什么集中一个类：清理点有 5 处（上传失败 / 取消 / 超时 / 失败超期 / 项目过期），
 * 分散写必然漏一处，最后的表现就是"磁盘只涨不降"。</p>
 *
 * <p>保留口径（见 06-结项冲刺步骤 §3.3）：</p>
 * <ul>
 *   <li>失败任务报告：留现场排障，{@code failed-report-minutes} 后清；</li>
 *   <li>成功任务报告：{@code success-report-days} 后清（报告可重扫再生）；</li>
 *   <li>解压目录：项目过期（或老项目超过 {@code work-days}）后清，并删除项目行
 *       （外键级联删除任务与明细）。</li>
 * </ul>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class StorageCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(StorageCleaner.class);

    private final ScanProperties scanProperties;

    private final UploadProperties uploadProperties;

    private final RetentionProperties retentionProperties;

    private final ScanTaskMapper scanTaskMapper;

    private final ProjectMapper projectMapper;

    /**
     * 构造注入。
     *
     * @param scanProperties      扫描配置（报告根目录）
     * @param uploadProperties    上传配置（解压根目录）
     * @param retentionProperties 保留策略
     * @param scanTaskMapper      任务 Mapper（按状态决定报告保留时长）
     * @param projectMapper       项目 Mapper（过期项目）
     */
    public StorageCleaner(ScanProperties scanProperties, UploadProperties uploadProperties,
                          RetentionProperties retentionProperties, ScanTaskMapper scanTaskMapper,
                          ProjectMapper projectMapper) {
        this.scanProperties = scanProperties;
        this.uploadProperties = uploadProperties;
        this.retentionProperties = retentionProperties;
        this.scanTaskMapper = scanTaskMapper;
        this.projectMapper = projectMapper;
    }

    /**
     * 删除某个任务的报告目录。
     *
     * @param taskNo 任务号（目录名）
     */
    public void deleteReport(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            return;
        }
        deleteRecursively(Path.of(scanProperties.getReportRoot(), taskNo), "报告目录");
    }

    /**
     * 删除某个项目的解压目录。
     *
     * @param storagePath 项目解压目录（绝对或相对路径）
     */
    public void deleteWorkDir(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        deleteRecursively(Path.of(storagePath), "解压目录");
    }

    /**
     * 周期性清理：过期报告、孤儿报告目录、过期项目。
     *
     * <p>cron 可配（默认每天 02:30），验收时把间隔调小即可观察效果。</p>
     */
    @Scheduled(cron = "${depcheck.retention.purge-cron:0 30 2 * * ?}")
    public void purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        int removedReports = purgeReports(now);
        int removedProjects = purgeProjects(now);
        if (removedReports > 0 || removedProjects > 0) {
            LOG.info("磁盘清理完成：报告目录 {} 个、过期项目 {} 个", removedReports, removedProjects);
        }
    }

    /** 按任务状态与目录修改时间清理报告目录。 */
    private int purgeReports(LocalDateTime now) {
        Path reportRoot = Path.of(scanProperties.getReportRoot());
        if (!Files.isDirectory(reportRoot)) {
            return 0;
        }
        Map<String, ScanTask> taskByNo = new HashMap<>(64);
        List<ScanTask> tasks = scanTaskMapper.selectList(new LambdaQueryWrapper<ScanTask>()
                .select(ScanTask::getTaskNo, ScanTask::getStatus));
        for (ScanTask task : tasks) {
            taskByNo.put(task.getTaskNo(), task);
        }

        LocalDateTime failedCutoff = now.minusMinutes(retentionProperties.getFailedReportMinutes());
        LocalDateTime successCutoff = now.minusDays(retentionProperties.getSuccessReportDays());

        int removed = 0;
        try (Stream<Path> dirs = Files.list(reportRoot)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String name = dir.getFileName().toString();
                ScanTask task = taskByNo.get(name);
                LocalDateTime cutoff;
                if (task == null) {
                    // 孤儿目录（任务已删 / 一致性自检产物）：按成功口径清理
                    cutoff = successCutoff;
                } else if (TaskStatus.FAILED.equals(task.getStatus())
                        || TaskStatus.CANCELED.equals(task.getStatus())) {
                    cutoff = failedCutoff;
                } else {
                    cutoff = successCutoff;
                }
                if (lastModified(dir).isBefore(cutoff)) {
                    if (deleteRecursively(dir, "过期报告")) {
                        removed++;
                    }
                }
            }
        } catch (IOException ex) {
            LOG.warn("遍历报告目录失败 root={}", reportRoot, ex);
        }
        return removed;
    }

    /**
     * 清理过期项目：删解压目录 + 删项目行（外键级联清任务与明细）。
     *
     * <p>只清"明确设置过 {@code expire_at} 且已到期"的项目。
     * {@code expire_at} 为 NULL 的历史项目一律不动——否则一次配置失误就会把历史扫描记录连根删掉
     * （这条规则是实测踩出来的，见 06-结项冲刺步骤 §3.3）。</p>
     */
    private int purgeProjects(LocalDateTime now) {
        if (retentionProperties.getProjectDays() <= 0) {
            return 0;
        }
        List<Project> expired = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .isNotNull(Project::getExpireAt)
                .le(Project::getExpireAt, now));
        int removed = 0;
        for (Project project : expired) {
            deleteWorkDir(project.getStoragePath());
            projectMapper.deleteById(project.getId());
            removed++;
            LOG.info("清理过期项目 projectId={} name={} expireAt={}",
                    project.getId(), project.getName(), project.getExpireAt());
        }
        return removed;
    }

    /** 目录修改时间；取不到时返回"很久以前"，让清理逻辑倾向于删除。 */
    private LocalDateTime lastModified(Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(),
                    java.time.ZoneId.systemDefault());
        } catch (IOException ex) {
            return LocalDateTime.MIN;
        }
    }

    /**
     * 递归删除（失败只记日志，不抛异常——清理不该影响主流程）。
     *
     * @param target 目标目录
     * @param label  日志标签
     * @return 是否真的删除了内容
     */
    private boolean deleteRecursively(Path target, String label) {
        if (target == null || !Files.exists(target)) {
            return false;
        }
        Path absolute = target.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(absolute)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    LOG.debug("删除文件失败 path={}", path, ex);
                }
            });
            LOG.debug("已清理{} path={}", label, absolute);
            return true;
        } catch (IOException ex) {
            LOG.warn("清理{}失败 path={}", label, absolute, ex);
            return false;
        }
    }
}
