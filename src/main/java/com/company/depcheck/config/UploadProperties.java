package com.company.depcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传相关配置，对应 application.yaml 的 depcheck.upload.*。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "depcheck.upload")
public class UploadProperties {

    /** 默认解压上限：2GB。 */
    private static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024;

    /** 默认文件数上限。 */
    private static final int DEFAULT_MAX_FILES = 50000;

    /** 解压根目录。 */
    private String root = "./data/work";

    /** 解压后总字节上限。 */
    private long maxUncompressedBytes = DEFAULT_MAX_BYTES;

    /** 解压后文件数上限。 */
    private int maxFileCount = DEFAULT_MAX_FILES;

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public long getMaxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    public void setMaxUncompressedBytes(long maxUncompressedBytes) {
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    public int getMaxFileCount() {
        return maxFileCount;
    }

    public void setMaxFileCount(int maxFileCount) {
        this.maxFileCount = maxFileCount;
    }
}
