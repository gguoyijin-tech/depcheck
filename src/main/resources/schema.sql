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

-- D8：取消标记与阶段名（取消采用"标记 + 阶段边界检查"，不强行杀线程）
ALTER TABLE scan_task ADD COLUMN IF NOT EXISTS cancel_requested TINYINT NOT NULL DEFAULT 0;
ALTER TABLE scan_task ADD COLUMN IF NOT EXISTS stage VARCHAR(32) NULL;

-- 按状态查任务（D9 列表页要用）
CREATE INDEX IF NOT EXISTS idx_task_status ON scan_task (status);
CREATE INDEX IF NOT EXISTS idx_task_project ON scan_task (project_id);

-- ============================================================================
-- D10：数据库层兜底（关键约束不靠代码判断）
-- 说明：
--   1) 全部用 IF NOT EXISTS，schema.sql 每次启动都会执行，必须幂等；
--   2) 加约束前先查过脏数据（重复明细 / 孤儿行 / 非法状态），当前库为 0；
--   3) 状态机同样靠库层兜底：所有状态写入都是 "UPDATE ... WHERE status=?"。
-- ============================================================================

-- ① 取值域：状态 / 阶段 / 进度 / 取消标记 / 耗时
ALTER TABLE scan_task ADD CONSTRAINT IF NOT EXISTS ck_task_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELED'));
ALTER TABLE scan_task ADD CONSTRAINT IF NOT EXISTS ck_task_progress
    CHECK (progress BETWEEN 0 AND 100);
ALTER TABLE scan_task ADD CONSTRAINT IF NOT EXISTS ck_task_cancel
    CHECK (cancel_requested IN (0, 1));
ALTER TABLE scan_task ADD CONSTRAINT IF NOT EXISTS ck_task_duration
    CHECK (duration_ms IS NULL OR duration_ms >= 0);
ALTER TABLE scan_task ADD CONSTRAINT IF NOT EXISTS ck_task_stage
    CHECK (stage IS NULL OR stage IN ('QUEUED', 'PREPARE', 'SCAN', 'ANALYZE',
                                      'REPORT', 'PERSIST', 'DONE', 'FAILED', 'CANCELED', 'TIMEOUT'));

-- ② 唯一性：任务号防重复（重放/重试）；项目目录不可被两个项目引用
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_no      ON scan_task (task_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_path ON project (storage_path);

-- ③ 外键 + 级联：明细不许挂到不存在的任务；删任务/删项目自动清明细
ALTER TABLE scan_task ADD CONSTRAINT IF NOT EXISTS fk_task_project
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;
ALTER TABLE dependency ADD CONSTRAINT IF NOT EXISTS fk_dep_task
    FOREIGN KEY (task_id) REFERENCES scan_task (id) ON DELETE CASCADE;
ALTER TABLE vulnerability ADD CONSTRAINT IF NOT EXISTS fk_vuln_task
    FOREIGN KEY (task_id) REFERENCES scan_task (id) ON DELETE CASCADE;
ALTER TABLE vulnerability ADD CONSTRAINT IF NOT EXISTS fk_vuln_dep
    FOREIGN KEY (dependency_id) REFERENCES dependency (id) ON DELETE CASCADE;

-- ④ 明细去重：同一任务里同一文件只允许一行；同一依赖同一 CVE 只允许一行
--    （file_path 为 NULL 的行不受限：H2 把 NULL 视为互不相同）
CREATE UNIQUE INDEX IF NOT EXISTS uk_dep_task_file ON dependency (task_id, file_path);
CREATE UNIQUE INDEX IF NOT EXISTS uk_vuln_task_cve ON vulnerability (task_id, dependency_id, cve_id);

-- ⑤ 其余取值域
ALTER TABLE dependency ADD CONSTRAINT IF NOT EXISTS ck_dep_risk
    CHECK (license_risk IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'));
ALTER TABLE vulnerability ADD CONSTRAINT IF NOT EXISTS ck_vuln_suppressed
    CHECK (suppressed IN (0, 1));
ALTER TABLE vulnerability ADD CONSTRAINT IF NOT EXISTS ck_vuln_score
    CHECK (cvss_score IS NULL OR (cvss_score >= 0 AND cvss_score <= 10));

