# Postman 测试集说明（D5 + D8）

## 1. 导入与准备

1. Postman → `Import` → 选 `postman/depcheck.postman_collection.json`。
2. 启动后端（IDEA Run，或 `java -jar target/depcheck-1.0.0.jar`），确认 `/api/ping` 通。
3. 改集合变量 **`baseUrl`** 为实际地址：
   - IDEA 里跑（默认端口）：`http://127.0.0.1:8080`
   - 命令行换端口跑的：`http://127.0.0.1:18081` 之类
4. 集合变量一览（右上角 Variables，一般不用改）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `baseUrl` | `http://127.0.0.1:8080` | **必须按实际端口改** |
| `sessionId` | `d8test` | 主会话，所有请求都带 `X-Session-Id` |
| `otherSessionId` | `someone-else` | 只用于"会话隔离"用例 |
| `projectId` | 空 | 由 **04 上传** 自动写入 |
| `scanProjectId` | 空 | **手动填**：取消一致性用例（37–40）要用**有 jar 的项目**（petclinic），填它的 id |
| `taskId` / `taskNo` | 空 | 由 **10 创建任务** 自动写入 |
| `cancelTaskId` | 空 | 由 **37 创建待取消任务** 自动写入 |
| `cancelPollCount` | `0` | 39 号轮询计数 |
| `pollCount` | `0` | 轮询计数，创建任务时自动归零 |

> **关键**：`projectId` 是自动填的，但前提是先跑过 **04 上传**。如果直接跑 10，body 会变成 `{"projectId": }` 这种非法 JSON（报 400）。

## 2. 建议的运行顺序

> **先跑 `00 登录`**：除 `/api/ping` 与 `/api/auth/login` 外，所有接口都要 `Authorization: Bearer <token>`；
> 登录成功后脚本自动写入 `token` 与 `sessionId` 变量（集合级已配 Bearer 鉴权）。
> 固定账号：`depcheck` / `Depcheck@2026`（`depcheck.auth.*` 可改）。

| 顺序 | 请求 | 期望 |
|---|---|---|
| 0 | `00a 登录` → `00b/00c/00d` | 拿到 token；错误密码 `1002`；未登录 `401` |
| 1 | `01 健康检查` | `code=0`，响应头有 `X-Trace-Id` |
| 2 | `04 上传-正常 zip` | `code=0`，自动写入 `projectId` |
| 3 | `10 创建任务` | `code=0`，`status=QUEUED/RUNNING`，自动写入 `taskId` |
| 4 | `12 轮询直到结束` | 见 §3；最终 `SUCCESS` 且 `progress=100` |
| 5 | `13 任务列表` / `14 按状态` | 列表含刚建的任务，按 id 倒序 |
| 6 | `16 取消已结束的任务` | `1001 任务已结束，无法取消`（**必须在 12 之后跑**） |
| 7 | `17 会话隔离` / `18 不存在` | 都是 `1003 资源不存在` |
| 8 | `19 → 20 → 21 取消链路` | 20 返回 `code=0`；21 看到 `CANCELED`（排队中取消是毫秒级；执行中取消要等阶段边界，最多约 12 秒） |
| 9 | `05 ~ 08 上传异常` | `2002 / 2002 / 2002 / 1001` |
| 10 | `30 结果概览` → `34` | 见 §2.1：依赖/漏洞总数、等级与许可证分布 |
| 11 | `37 → 38 → 39 → 40` | **取消一致性**：40 必须是 `CANCELED` 且 `0 / 0`（见 §2.2） |

### 2.1 结果查询用例（D10 新增）

| 请求 | 断言要点 |
|---|---|
| `30 结果概览` | 严重度合计 = 漏洞总数、许可证合计 = 依赖总数（两个分布不漏项） |
| `31 依赖分页` | `total` 与概览一致；`page/size` 回显；每行有 `licenseRisk` |
| `32 依赖搜索` | 返回行的 `name+purl` 都含关键字 |
| `33 漏洞分页` | CVSS 严格降序；`dependencyName` 有值 |
| `34 严重度过滤` | 返回行全是 `CRITICAL` |
| `35 / 36` | 别人会话、不存在任务 → `1003` |

### 2.2 取消一致性用例（37–40，必须用 petclinic）

> **为什么不能用 `sample-project.zip`**：那个包只有 1 个文件、**没有 jar**，DC 扫出来就是 **0 依赖 / 0 漏洞**，
> 而且不到 1 秒就 `SUCCESS` 了。此时概览也是 `0 / 0`，但它证明的是"这个项目本来就没依赖"，
> 不是"取消把明细回滚了"——证据不成立（实测：任务 424 就是这种情况，断言 `状态已非 SUCCESS` 失败）。

正确做法：把 `scanProjectId` 填成 **petclinic 项目**的 id（有 85 个依赖），再跑：

| 请求 | 期望 |
|---|---|
| `37 创建待取消任务` | `code=0`，状态 `QUEUED/RUNNING`，记录 `cancelTaskId` |
| `38 取消` | `code=0` |
| `39 等取消生效`（Runner 跑，`Delay=1000ms`） | 1–12 秒内变 `CANCELED`（否则轮询 30 次后报错） |
| `40 一致性` | **`status=CANCELED` 且 `deps=0`、`vulns=0`** |

实测对照（同一套库）：

```
任务 425（petclinic，创建后立即取消）→ 1 秒后 CANCELED(20%) → summary 0 / 0
任务 358（petclinic，正常跑完）      → SUCCESS(100%)        → summary 85 / 53
```

只有这两个放一起，才能说明"取消的任务一条明细都没落库"。

## 3. 第 12 条怎么跑（轮询）

它有**自循环**逻辑：任务还是 `QUEUED/RUNNING` 就 `setNextRequest` 再发一次自己，最多 40 次；任务结束就停下并断言最终状态。

- **Collection Runner 方式（推荐）**：选这一条 → Runner 里设 `Iterations = 1`、`Delay = 1000 ms` → Run。这样最接近前端 1 秒轮询。
- **手动 Send**：会连发（每次几十毫秒），40 次后自动停，也能看到最终状态，只是请求数多。

如果 40 次还没结束（大工程），把脚本里的 `n < 40` 调大。

## 4. 第 22 / 23 条怎么跑（并发/队列）

这两条要用 **Runner + 多次迭代**：

| 用例 | 设置 | 期望 |
|---|---|---|
| `22 并发提交-3 个任务` | Iterations = 3，Delay = 0 | 3 个全部受理（`code=0`），此时应有 **2 个 RUNNING + 1 个 QUEUED** |
| `23 队列压力` | **先把 `depcheck.scan.pool.queue` 改 1 并重启**，Iterations = 4 | 前 3 个受理，第 4 个 `1004 系统繁忙` |

跑完 22 后，在 `GET /api/scan-tasks?status=RUNNING` 里应看到 2 条，`?status=QUEUED` 里 1 条。

> 内存只有 4 GB，别把 `max` 调大来"压测通过"——并行 2 是实测出来的安全值（见 `04-7` §4.1）。

## 5. 想看到 85 依赖 / 53 漏洞

`postman/testdata/sample-project.zip` 只有 4 个文件，扫出来是个位数依赖，**看不出效果**。要复现基线，请上传 petclinic：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory(
  'D:\dc\samples\spring-petclinic', "$env:TEMP\petclinic.zip",
  [System.IO.Compression.CompressionLevel]::Optimal, $true)
# 然后在 04 上传里把 file 换成这个 zip（或直接在 Postman 里点 file 字段重选）
```

扫完停掉后端，查库核对（H2 文件库同一时间只允许一个进程打开）：

```powershell
$h2 = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar"
& java -cp $h2 org.h2.tools.Shell -url "jdbc:h2:file:./data/depcheck;MODE=MySQL" `
  -user sa -password "" `
  -sql "select task_id,count(*) from dependency group by task_id order by task_id desc limit 3;
        select task_id,count(*) from vulnerability group by task_id order by task_id desc limit 3;"
```

期望：最新任务 `85` 和 `53`。

## 6. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 所有请求 `connect ECONNREFUSED` | 后端没起 / 端口不对 | 改 `baseUrl`；`netstat -ano \| findstr 8080` 看端口 |
| 10 报 `400` 且提示 `projectId 不能为空` | 没先跑 04，`projectId` 是空 | 先跑 04，或手动在 Variables 里填一个项目 id |
| 04 报 `400` 或 "找不到文件" | `testdata` 的绝对路径变了（换机器/换目录） | 在 Body → form-data → file 里重新选一次文件 |
| 04 返回 `2002` | 上传的确实是非法 zip | 换 `sample-project.zip` 或 petclinic 包 |
| 10 返回 `1003` | `projectId` 不属于当前 `sessionId` | 用创建该项目的同一 `X-Session-Id` |
| 10 返回 `1004` | 扫描队列满（默认 8） | 等前一个跑完，或临时把 queue 调大 |
| 21 一直是 `RUNNING` | 取消只在**阶段边界**生效，DC 分析阶段不可中断 | 最多等约 12 秒（petclinic）；超时另有看门狗兜底 |
| 16 报断言失败"前置条件" | 12 还没跑完，任务仍在执行 | 先跑完 12 再跑 16 |
| 40 状态是 `SUCCESS`、断言失败 | 用了 `sample-project`（1 个文件、无 jar，秒级扫完，本来就 0 依赖） | 把 `scanProjectId` 填成 **petclinic** 的项目 id 再跑 37–40 |
| 39 一直轮询到报错 | 任务在 12 秒内没到阶段边界（超大工程） | 把 39 的 `n < 30` 调大；或先确认任务确实在跑 |

## 7. 和文档的对应

| 用例 | 文档 |
|---|---|
| 上传 5 类异常 | `04-2-上传接口开发步骤.md` §8 |
| 任务 4 个接口 | `04-5-扫描任务异步化步骤.md` §7、§10 |
| 状态机 / 取消 / 超时语义 | `04-5` §5–§6、`05-整体流程说明.md` §6 |
| 并发度与队列 | `04-7-扫描结果一致性与并发设计.md` §4 |
