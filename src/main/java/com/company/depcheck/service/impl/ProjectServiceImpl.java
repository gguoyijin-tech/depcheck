package com.company.depcheck.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.common.util.ZipUtils;
import com.company.depcheck.config.UploadProperties;
import com.company.depcheck.mapper.ProjectMapper;
import com.company.depcheck.model.entity.Project;
import com.company.depcheck.service.ProjectService;

/**
 * 项目业务实现。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectServiceImpl.class);

    /** 默认上传文件名。 */
    private static final String DEFAULT_FILE_NAME = "upload.zip";

    /** 允许的文件扩展名。 */
    private static final String ZIP_SUFFIX = ".zip";

    /** 原始 ZIP 落盘文件名。 */
    private static final String SOURCE_ZIP_NAME = "source.zip";

    /** 解压子目录名。 */
    private static final String DATA_DIR_NAME = "data";

    /** 默认会话标识。 */
    private static final String DEFAULT_SESSION = "default";

    /** ZIP 魔数长度。 */
    private static final int MAGIC_LEN = 4;

    /** 数据库列长度上限。 */
    private static final int MAX_NAME_LEN = 128;
    private static final int MAX_ORIG_NAME_LEN = 255;
    private static final int MAX_SESSION_LEN = 64;

    /** 列表默认返回上限（防止无界全表查询）。 */
    private static final String LIMIT_SQL = "LIMIT 200";

    /** 同时解压的许可数：解压占 IO/CPU，限制并发避免拖垮扫描（见 04-7 §4.5）。 */
    private static final Semaphore UNZIP_PERMITS = new Semaphore(2);

    private final ProjectMapper projectMapper;
    private final UploadProperties uploadProperties;

    /**
     * 构造注入 Mapper 与上传配置。
     *
     * @param projectMapper    项目表 Mapper
     * @param uploadProperties 上传配置
     */
    public ProjectServiceImpl(ProjectMapper projectMapper, UploadProperties uploadProperties) {
        this.projectMapper = projectMapper;
        this.uploadProperties = uploadProperties;
    }

    @Override
    public List<Project> listAll() {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .orderByDesc(Project::getId)
                .last(LIMIT_SQL));
    }

    @Override
    public List<Project> listBySession(String sessionId) {
        String normalizedSession = normalizeSession(sessionId);
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getOwnerSession, normalizedSession)
                .orderByDesc(Project::getId)
                .last(LIMIT_SQL));
    }

    @Override
    public Project getById(Long id) {
        if (id == null) {
            return null;
        }
        return projectMapper.selectById(id);
    }

    @Override
    public Project getById(Long id, String sessionId) {
        if (id == null) {
            return null;
        }
        Project project = projectMapper.selectById(id);
        String normalizedSession = normalizeSession(sessionId);
        if (project == null || !normalizedSession.equals(project.getOwnerSession())) {
            return null;
        }
        return project;
    }

    @Override
    public Project upload(MultipartFile file, String sessionId) {
        // ① 基础校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        String rawName = file.getOriginalFilename();
        String originalName = StringUtils.hasText(rawName) ? rawName.trim() : DEFAULT_FILE_NAME;
        if (originalName.length() > MAX_ORIG_NAME_LEN
                || !originalName.toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX)) {
            throw new BusinessException(ResultCode.INVALID_ZIP);
        }
        String normalizedSession = normalizeSession(sessionId);

        // ② 魔数校验：扩展名可以改，文件头改不了
        byte[] head = new byte[MAGIC_LEN];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) < MAGIC_LEN || !ZipUtils.hasZipMagic(head)) {
                throw new BusinessException(ResultCode.INVALID_ZIP);
            }
        } catch (IOException ex) {
            LOG.warn("读取上传文件头失败 name={}", originalName, ex);
            throw new BusinessException(ResultCode.INVALID_ZIP, ex);
        }

        // ③ 解压限流：解压是 IO + CPU 密集操作，最多允许 2 个同时进行，
        //    避免把磁盘与扫描线程饿死（见 04-7 §4.5）
        if (!UNZIP_PERMITS.tryAcquire()) {
            LOG.warn("解压并发已满，拒绝上传 name={}", originalName);
            throw new BusinessException(ResultCode.SERVER_BUSY);
        }

        // ④ UUID 落盘（禁止用原始文件名做路径）
        String uuid = UUID.randomUUID().toString().replace("-", "");
        Path root = Paths.get(uploadProperties.getRoot()).toAbsolutePath().normalize();
        Path dir = root.resolve(uuid);
        Path dataDir = dir.resolve(DATA_DIR_NAME);
        try {
            Files.createDirectories(dataDir);
            Path zipFile = dir.resolve(SOURCE_ZIP_NAME);
            file.transferTo(zipFile.toFile());

            int fileCount = ZipUtils.unzip(zipFile, dataDir,
                    uploadProperties.getMaxUncompressedBytes(),
                    uploadProperties.getMaxFileCount());

            Project project = new Project();
            project.setName(truncate(stripZipSuffix(originalName), MAX_NAME_LEN));
            project.setOriginalName(originalName);
            project.setStoragePath(dataDir.toString());
            project.setSizeBytes(file.getSize());
            project.setFileCount(fileCount);
            project.setOwnerSession(normalizedSession);
            project.setCreatedAt(LocalDateTime.now());
            projectMapper.insert(project);

            LOG.info("项目上传成功 projectId={} name={} files={} sizeBytes={}",
                    project.getId(), project.getName(), fileCount, project.getSizeBytes());
            return project;
        } catch (BusinessException ex) {
            deleteQuietly(dir);
            throw ex;
        } catch (IOException ex) {
            deleteQuietly(dir);
            LOG.warn("保存上传文件失败 name={}", originalName, ex);
            throw new BusinessException(ResultCode.INVALID_ZIP, ex);
        } finally {
            UNZIP_PERMITS.release();
        }
    }

    /** 去掉 .zip 后缀，作为项目名。 */
    private String stripZipSuffix(String name) {
        int idx = name.toLowerCase(Locale.ROOT).lastIndexOf(ZIP_SUFFIX);
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /** 校验并归一化 sessionId。 */
    private String normalizeSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return DEFAULT_SESSION;
        }
        String trimmed = sessionId.trim();
        if (trimmed.length() > MAX_SESSION_LEN) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        return trimmed;
    }

    /** 截断超长字符串。 */
    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    /** 失败时清掉半成品目录，避免磁盘越积越多。 */
    private void deleteQuietly(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        LOG.debug("清理临时文件失败 path={}", path, ex);
                    }
                });
            }
        } catch (IOException ex) {
            LOG.debug("遍历清理临时目录失败 dir={}", dir, ex);
        }
    }
}
