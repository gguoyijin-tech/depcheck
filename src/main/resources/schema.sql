-- 项目表：上传即建；original_name 仅展示，落盘用 UUID
CREATE TABLE IF NOT EXISTS project (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128)  NOT NULL,
    original_name VARCHAR(255)  NOT NULL,
    storage_path  VARCHAR(512)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    file_count    INT           NOT NULL DEFAULT 0,
    owner_session VARCHAR(64)   NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at     TIMESTAMP     NULL
);

-- 扫描任务：D7 只写一条 SUCCESS 记录；D8 在这张表上加状态机/进度
CREATE TABLE IF NOT EXISTS scan_task (
                                         id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         task_no        VARCHAR(64)   NOT NULL,
                                         project_id     BIGINT        NOT NULL,
                                         status         VARCHAR(16)   NOT NULL,
                                         progress       INT           NOT NULL DEFAULT 0,
                                         scan_mode      VARCHAR(16)   NOT NULL DEFAULT 'BUILD',
                                         engine_version VARCHAR(32)   NULL,
                                         nvd_version    VARCHAR(64)   NULL,
                                         started_at     TIMESTAMP     NULL,
                                         finished_at    TIMESTAMP     NULL,
                                         duration_ms    BIGINT        NULL,
                                         error_code     INT           NULL,
                                         error_msg      VARCHAR(512)  NULL,
                                         created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 依赖清单：一条任务 N 行
CREATE TABLE IF NOT EXISTS dependency (
                                          id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          task_id      BIGINT        NOT NULL,
                                          name         VARCHAR(255)  NULL,
                                          version      VARCHAR(128)  NULL,
                                          ecosystem    VARCHAR(32)   NULL,
                                          purl         VARCHAR(512)  NULL,
                                          file_path    VARCHAR(1024) NULL,
                                          license_name VARCHAR(255)  NULL,
                                          license_risk VARCHAR(16)   NULL,
                                          created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dependency_task ON dependency (task_id);

-- 漏洞明细：一条依赖 N 行
CREATE TABLE IF NOT EXISTS vulnerability (
                                             id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             task_id       BIGINT        NOT NULL,
                                             dependency_id BIGINT        NULL,
                                             cve_id        VARCHAR(64)   NOT NULL,
                                             cvss_score    DECIMAL(3,1)  NULL,
                                             severity      VARCHAR(16)   NULL,
                                             title         VARCHAR(512)  NULL,
                                             description   VARCHAR(4000) NULL,
                                             cwe           VARCHAR(255)  NULL,
                                             ref_url       VARCHAR(1000) NULL,
                                             fix_version   VARCHAR(128)  NULL,
                                             suppressed    TINYINT       NOT NULL DEFAULT 0,
                                             created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vuln_task ON vulnerability (task_id);
CREATE INDEX IF NOT EXISTS idx_vuln_dep  ON vulnerability (dependency_id);
