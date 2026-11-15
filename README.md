# depcheck · 应用依赖安全与合规分析平台（后端）

把 OWASP Dependency-Check 这个命令行引擎，包装成「上传 ZIP → 自动扫描 → 出 HTML/Excel 报告 → 可查历史」的内部 Web 服务。

## 功能

| 模块 | 状态 |
|---|---|
| ZIP 上传（魔数校验 / 防 Zip Slip / 限额解压 / UUID 落盘） | ✅ |
| 进程内调用 Dependency-Check 扫描，产出 HTML + JSON 报告 | ✅ |
| 报告解析入库（依赖 / 漏洞明细 / 许可证风险四档） | ✅ |
| 扫描任务异步化（状态机 / 进度 / 取消 / 超时） | 🚧 D8 |
| 前端（上传 / 任务列表 / 结果详情） | ⏳ D9–D11 |
| 报告下载与 Excel 导出 | ⏳ D12–D13 |

## 技术栈

| 层 | 选型 |
|---|---|
| 运行时 | JDK 21 |
| 框架 | Spring Boot 3.2.12（单体，非微服务） |
| 持久化 | H2 嵌入式（文件库）+ MyBatis-Plus 3.5.5 |
| 引擎 | OWASP Dependency-Check Core 12.1.0（进程内调用） |
| 构建 | Maven 3.9.16 |

> 依赖版本全部锁定；`h2` / `jackson` / `commons-lang3` 三个版本属性是为了兼容 DC 12.1.0 而覆盖的，**不要随意改回**。

## 快速开始

前置：JDK 21、Maven 3.9+，以及一份已建好的 NVD 漏洞库（约 663 MB，目录结构见 `depcheck.scan.data-dir`）。

```bash
# 1. 编译打包
mvn -DskipTests package

# 2. 启动（默认 8080）
java -jar target/depcheck-1.0.0.jar

# 3. 健康检查
curl http://127.0.0.1:8080/api/ping
```

首次启动会自动用 `src/main/resources/schema.sql` 建表，业务库落在 `./data/depcheck.mv.db`。

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ping` | 健康检查（带 traceId） |
| GET | `/api/projects` | 当前会话的项目列表 |
| GET | `/api/projects/{id}` | 项目详情 |
| POST | `/api/projects/upload` | 上传 ZIP（form-data，字段名 `file`） |
| POST | `/api/scan-tasks` | 创建扫描任务（D8） |
| GET | `/api/scan-tasks/{id}` | 任务状态与进度（D8） |
| POST | `/api/scan-tasks/{id}/cancel` | 取消任务（D8） |

会话隔离通过请求头 `X-Session-Id` 传递，未传时默认 `default`。

## 目录结构

```
src/main/java/com/company/depcheck
├─ common      统一响应 / 全局异常 / traceId / ZIP 工具
├─ config      H2、线程池、上传与扫描配置
├─ controller  接口层
├─ service     业务层（含扫描结果入库）
├─ mapper      MyBatis-Plus Mapper
├─ model       entity / dto / vo
├─ engine      扫描引擎抽象与进程内实现（不依赖 service/mapper）
└─ dev         临时的启动即扫描入口（D8 删除）
```

## 注意

- 不提交 `data/`、`target/`、`logs/`、`.idea/`（已在 `.gitignore` 中）；
- 扫描引擎依赖较大的第三方包，构建产物约 58 MB，属正常；
- 内网/离线环境：DC 的联网分析器（RetireJS / OSS Index / Central）已在 `InProcessDcEngine` 中关闭。
