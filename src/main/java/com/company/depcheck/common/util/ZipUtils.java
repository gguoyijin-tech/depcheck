package com.company.depcheck.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;

/**
 * ZIP 工具：魔数校验 + 防 Zip Slip + 解压限额。
 *
 * @author depcheck
 * @since 1.0.0
 */
public final class ZipUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ZipUtils.class);

    /** ZIP 头字节数。 */
    private static final int MAGIC_LEN = 4;

    /** ZIP 头 4 字节：50 4B 03 04。 */
    private static final int ZIP_MAGIC_0 = 0x50;
    private static final int ZIP_MAGIC_1 = 0x4B;
    private static final int ZIP_MAGIC_2 = 0x03;
    private static final int ZIP_MAGIC_3 = 0x04;

    /** 字节掩码。 */
    private static final int BYTE_MASK = 0xFF;

    /** 单次读写缓冲。 */
    private static final int BUFFER_SIZE = 8192;

    private ZipUtils() {
    }

    /**
     * 校验文件头是不是 ZIP 魔数。
     *
     * @param head 文件前 4 个字节
     * @return true 表示像 ZIP
     */
    public static boolean hasZipMagic(byte[] head) {
        return head != null && head.length >= MAGIC_LEN
                && (head[0] & BYTE_MASK) == ZIP_MAGIC_0
                && (head[1] & BYTE_MASK) == ZIP_MAGIC_1
                && (head[2] & BYTE_MASK) == ZIP_MAGIC_2
                && (head[3] & BYTE_MASK) == ZIP_MAGIC_3;
    }

    /**
     * 安全解压：拒绝路径穿越、限制总字节与文件数。
     *
     * @param zipFile       zip 文件
     * @param targetDir     解压目标目录（必须是已存在的空目录）
     * @param maxTotalBytes 解压后总字节上限
     * @param maxFileCount  文件数上限
     * @return 解压出的文件数
     * @throws BusinessException 非法 zip / 超限时抛出
     */
    public static int unzip(Path zipFile, Path targetDir, long maxTotalBytes, int maxFileCount) {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        long totalBytes = 0L;
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                // ① 防 Zip Slip：规范化后必须仍在目标目录内
                Path out = normalizedTarget.resolve(entry.getName()).normalize();
                if (!out.startsWith(normalizedTarget)) {
                    LOG.warn("检测到 Zip Slip 路径穿越 entry={}", entry.getName());
                    throw new BusinessException(ResultCode.INVALID_ZIP);
                }

                // ② 文件数上限
                if (++fileCount > maxFileCount) {
                    LOG.warn("解压文件数超限 count={} limit={}", fileCount, maxFileCount);
                    throw new BusinessException(ResultCode.ZIP_LIMIT_EXCEEDED);
                }

                Files.createDirectories(out.getParent());
                long written = copy(zis, out);

                // ③ 总字节上限（按实际写出的字节算，防"声明很小、解压巨大"）
                totalBytes += written;
                if (totalBytes > maxTotalBytes) {
                    LOG.warn("解压总字节数超限 totalBytes={} limit={}", totalBytes, maxTotalBytes);
                    throw new BusinessException(ResultCode.ZIP_LIMIT_EXCEEDED);
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            LOG.warn("解压 ZIP 文件失败 file={}", zipFile, ex);
            throw new BusinessException(ResultCode.INVALID_ZIP, ex);
        }
        return fileCount;
    }

    private static long copy(InputStream in, Path out) throws IOException {
        long written = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream os = Files.newOutputStream(out)) {
            int len;
            while ((len = in.read(buffer)) > 0) {
                os.write(buffer, 0, len);
                written += len;
            }
        }
        return written;
    }
}
