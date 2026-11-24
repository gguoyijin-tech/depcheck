# web · 前端（D9 骨架）

Vue 3.4 + Element Plus 2.7 + Vite 5 + Pinia + axios。四个页面，严格按 `需求分析与开发计划.md` §5.9。

## 1. 启动

```powershell
cd 'E:\one drive\OneDrive\Desktop\cv push\online project\backend\depcheck\web'
npm install          # 首次
npm run dev          # http://localhost:5173
```

后端先起来（默认 8080）。前端只请求 `/api/**`，由 vite 代理转发，所以**没有跨域问题**，也不用配 baseURL。

后端不在 8080 时：

```powershell
$env:VITE_API_TARGET='http://127.0.0.1:18081'; npm run dev
```

> 注意 `localhost:5173`，不是 `127.0.0.1:5173`：vite 默认只监听 `localhost`（本机 IPv6/IPv4 解析差异），用 `127.0.0.1` 访问可能被拒。

## 2. 页面与路由

用的是 **hash 路由**（`/#/tasks`），因为后端还没有 SPA fallback，刷新 `/tasks` 会 404；等 D15 交给 Nginx 后再换 history 模式。

| 路由 | 页面 | 已做 | 待做 |
|---|---|---|---|
| `/#/login` | 登录（占位） | 输入昵称 → 生成并本地保存会话标识 | D11 换正式登录 |
| `/#/scan/new` | 上传与扫描 | 拖拽、`512 MB` 前置校验、上传进度、成功后一键提交扫描 | 参数表单（排除路径/报告格式）D10 |
| `/#/tasks` | 任务列表 | 状态筛选、3 秒轮询、进度条+阶段、取消、重新扫描 | 分页、下载 D10 |
| `/#/tasks/:id` | 任务详情 | 进度、阶段、耗时、失败原因、取消、2 秒轮询（结束自动停） | 结果页 Tab D10 |

## 3. 目录

```
web/
├─ index.html
├─ vite.config.js        /api 代理 + @ 别名
└─ src/
   ├─ main.js            挂载 Vue / Element Plus（中文 locale）/ Pinia / 路由
   ├─ App.vue             顶栏 + 内容区
   ├─ api/
   │  ├─ http.js          axios 实例：自动带 X-Session-Id、统一错误提示
   │  ├─ project.js       项目列表 / 详情 / 上传
   │  └─ task.js          建任务 / 详情 / 列表 / 取消
   ├─ router/index.js     4 个路由 + 登录守卫
   ├─ stores/session.js   会话标识（localStorage）
   ├─ utils/task.js       状态/阶段文案、耗时与时间格式化
   ├─ components/StatusTag.vue
   └─ views/              Login / Upload / TaskList / TaskDetail
```

## 4. 约定（和后端对齐）

| 事 | 约定 |
|---|---|
| 会话 | 所有请求带 `X-Session-Id`（后端据此隔离项目与任务） |
| 响应 | `{code, message, data}`；`code !== 0` 由 `http.js` 统一弹错并 reject |
| 错误码 | `1001` 参数 / `1003` 不存在或不属于你 / `1004` 系统繁忙 / `2002` 非法 ZIP / `3002` 超时 |
| 进度 | 后端是**阶段进度**（5/20/40/80/90/95/100），前端不要假装成精确百分比 |
| 轮询 | 列表 3 秒、详情 2 秒；任务结束后立即停止（`isActive` 判断） |
| 取消 | 排队中立即生效；执行中要等当前 DC 阶段结束（最多约 12 秒） |

## 5. 构建

```powershell
npm run build     # 产物 web/dist/
npm run preview   # 本地预览构建产物（不带 /api 代理）
```

`web/dist/` 与 `node_modules/` 已在 `web/.gitignore` 中忽略。

## 6. 已知待办

1. 浏览器实测一遍（上传 petclinic → 任务成功 → 85 / 53），见根目录 `05-整体流程说明.md` §9。
2. 结果页（依赖清单 / 漏洞清单 / 许可证 Tab）要等 D10 的后端查询接口。
3. 生产托管：`dist/` 交给 Nginx（`client_max_body_size 512m`、`proxy_read_timeout` 放大）。
