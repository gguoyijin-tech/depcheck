# 应用依赖安全与合规分析平台（Depcheck）

> 把 OWASP Dependency-Check 这个命令行安全引擎，包装成集团内**开箱即用、有界面、有报告、有文档**的依赖安全与许可证合规检测服务。

## 一、这个平台解决什么问题

集团内部累计了数万款 Java / 前端应用，第三方依赖数量持续增长，但存在三个现实问题：

| 问题 | 现状 | 平台的做法 |
|---|---|---|
| **漏洞看不见** | 各团队用的零散工具，扫描口径不一 | 统一用 OWASP Dependency-Check 9.x→12.x 引擎，一套基线 |
| **许可证说不清** | 引擎只给许可证字符串，不判合规 | 自研许可证策略：四档风险分级 + 黑白名单 + 待定通道 |
| **门槛太高** | 命令行参数复杂、漏洞库同步困难 | Web 上传 ZIP → 自动扫描 → 出 HTML/Excel 报告，零学习成本 |

配套产出**完整文档体系**（环境搭建 / 引擎原理 / 使用 / 配置排查 / 开发维护 / 总览），新人照文档可独立完成部署与一次扫描。

## 二、能力全景

| 层 | 能力 | 状态 |
|---|---|---|
| **引擎层** | 多语言依赖识别（Java/Maven 承诺级，NPM 次之）、NVD 漏洞匹配、许可证识别 | ✅ |
| | 漏洞库全量/增量同步、内网离线镜像 | ✅ |
| **服务层** | ZIP 上传（魔数校验 / 防 Zip Slip / 限额解压 / UUID 落盘） | ✅ |
| | 进程内调用引擎，产出 HTML + JSON 报告 | ✅ |
| | 报告解析入库（依赖 / 漏洞明细 / 许可证风险） | ✅ |
| | 扫描任务异步化（状态机 / 进度 / 取消 / 超时） | 🚧 开发中 |
| | 报告在线预览、下载、Excel 导出 | ⏳ 规划中 |
| | 许可证合规判定、漏洞白名单 | ⏳ 规划中 |
| **交互层** | 登录 / 上传提交 / 任务列表 / 结果详情 | ⏳ 规划中 |
| **交付层** | Docker Compose 一键部署、Nginx 反代 | ⏳ 规划中 |
| **文档层** | 6 份标准文档（见第七节） | ✅ 5 份已成稿 |

## 三、整体架构

```
浏览器
  │
  ├─ 前端 SPA（Vue 3 + Element Plus）        ← 规划中，将放在 web/
  │
  ├─ Nginx：静态资源 + /api 反代
  │
  └─ Spring Boot 3.2 单体应用（本仓库）
        ├─ Controller / Service / Mapper
        ├─ ScanTaskService  任务调度（线程池 dc-scan-*）
        ├─ ScanEngine 接口
        │     └─ InProcessDcEngine  →  OWASP Dependency-Check Core 12.1.0
        │                                   └─ NVD 漏洞库（H2 文件，约 663 MB）
        ├─ ReportParser / ScanPersistService  →  报告解析入库
        ├─ 业务库 H2（项目 / 任务 / 依赖 / 漏洞）
        └─ ReportStorage（本地；二期预留阿里云 OSS）
```

**关键设计**：业务层只依赖 `ScanEngine` 接口，引擎在进程内还是子进程、是 DC 12 还是未来的 osv-scanner，都只换实现类，不动上层。

## 四、技术栈

| 层 | 选型 |
|---|---|
| 运行时 | JDK 21 |
| 框架 | Spring Boot 3.2.12（单体架构，不拆微服务） |
| 持久化 | H2 嵌入式（文件库）+ MyBatis-Plus 3.5.5 |
| 引擎 | OWASP Dependency-Check Core 12.1.0（进程内调用） |
| 构建 | Maven 3.9.16 |
| 前端（规划） | Vue 3.4 + Element Plus + Vite |

> 版本全部锁定。`h2` / `jackson` / `commons-lang3` 三个版本属性是为兼容 DC 12.1.0 而覆盖的，**不要随意改回默认值**——否则出报告时会 `NoSuchMethodError`。

## 五、仓库结构

```
depcheck/
├─ src/main/java/com/company/depcheck
│  ├─ common      统一响应 / 全局异常 / traceId / ZIP 工具
│  ├─ config      线程池、上传与扫描配置
│  ├─ controller  接口层（项目、任务）
│  ├─ service     业务层（项目、扫描结果入库、任务）
│  ├─ mapper      MyBatis-Plus Mapper
│  ├─ model       entity / dto / vo
│  ├─ engine      引擎抽象与进程内实现（不依赖 service/mapper）
│  └─ dev         临时调试入口（D8 后删除）
├─ src/main/resources
│  ├─ application.yaml
│  ├─ schema.sql  建表脚本（启动自动执行）
│  └─ logback-spring.xml
├─ postman/       Postman 集合 + 测试用 ZIP（正常/非 ZIP/伪装/Zip Slip/空包）
└─ pom.xml
```

后续将增加 `web/`（前端）与 `docs/`（文档集）。

## 六、快速开始

前置：**JDK 21**、**Maven 3.9+**，以及一份已建好的 **NVD 漏洞库**（约 663 MB）。

> 漏洞库不在仓库里（体积太大且可重建）。首次使用需按文档《01-本地环境搭建手册》完成建库：下载 DC 12.1.0、拉取官方 JSON 数据源、生成 `cache.properties`、本地同步。建好后把 `depcheck.scan.data-dir` 指向该目录。

```bash
# 1. 编译打包（首次会拉 DC 依赖，产物约 58 MB）
mvn -DskipTests package

# 2. 启动（默认 8080）
java -jar target/depcheck-1.0.0.jar

# 3. 健康检查
curl http://127.0.0.1:8080/api/ping
```

首次启动自动执行 `schema.sql` 建表，业务库落在 `./data/depcheck.mv.db`。

## 七、接口一览

| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET | `/api/ping` | 健康检查（返回 traceId） | ✅ |
| GET | `/api/projects` | 当前会话的项目列表 | ✅ |
| GET | `/api/projects/{id}` | 项目详情 | ✅ |
| POST | `/api/projects/upload` | 上传 ZIP（form-data，字段名 `file`） | ✅ |
| POST | `/api/scan-tasks` | 创建扫描任务 | 🚧 |
| GET | `/api/scan-tasks/{id}` | 任务状态与进度 | 🚧 |
| GET | `/api/scan-tasks` | 任务列表（按状态筛选） | 🚧 |
| POST | `/api/scan-tasks/{id}/cancel` | 取消任务 | 🚧 |

统一响应：`{"code":0,"message":"success","data":...}`；错误码分段：`1xxx` 通用、`2xxx` 上传、`3xxx` 扫描、`4xxx` 报告。
会话隔离通过请求头 `X-Session-Id`，未传默认 `default`。

## 八、文档体系

| 文档 | 内容 |
|---|---|
| 01 · 本地环境搭建手册 | JDK/Maven/引擎安装、建库、离线镜像、基线记录 |
| 02 · Dependency-Check 使用步骤 | 扫描对象、参数、报告解读、白名单、CI 门禁 |
| 04 · 工程搭建步骤 | IDEA 建工程 → 骨架 → 首个接口跑通 |
| 04-2 · 上传接口开发步骤 | ZIP 校验、防 Zip Slip、限额解压 |
| 04-3 · 扫描引擎接入步骤 | dc-core 引入、版本对齐、进程内扫描 |
| 04-4 · JSON 解析与批量入库步骤 | 报告解析、批量插入、许可证分级 |
| 04-5 · 扫描任务异步化步骤 | 状态机、进度、取消、超时 |

（文档正在整理进 `docs/` 目录。）

## 九、路线图

- [x] D1–D4 环境搭建 + 后端骨架 + 项目查询接口
- [x] D5 ZIP 上传（含 5 类安全用例）
- [x] D6 进程内扫描（85 依赖 / 53 漏洞 / 15 秒）
- [x] D7 报告解析与批量入库
- [ ] D8 任务异步化（状态机 / 进度 / 取消）
- [ ] D9–D11 前端页面与联调
- [ ] D12–D13 HTML 报告定制 + Excel 导出
- [ ] D14 许可证合规与白名单
- [ ] D15 Docker Compose 部署
- [ ] D16–D19 文档定稿与导出 PDF

## 十、约束与注意

- **单体架构**，不引微服务、消息队列、K8s；
- **H2 嵌入式**，业务库与 DC 的 NVD 库是两个独立文件，不能混用；
- **不修改 DC 核心源码**，只通过 `Engine` API + 配置调用；
- 不提交 `data/`、`target/`、`logs/`、`.idea/`（见 `.gitignore`）；
- 内网/离线环境：DC 的联网分析器（RetireJS / OSS Index / Central）已在 `InProcessDcEngine` 关闭；
- 本平台仅用于集团内部，不上传含敏感信息的代码。
