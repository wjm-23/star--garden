# 星芽花园 RESTful API 接口文档

> 交付于开题报告进度表第 15-16 周：「编写接口文档及前后端数据交互规范」。
> 交互式文档（Swagger UI）请启动后端后访问 <http://localhost:8081/swagger-ui.html>，
> OpenAPI 3 描述文件位于 `/v3/api-docs`。本文档为论文第五章引用的静态版本。

## 一、通用约定

| 项目 | 约定 |
|---|---|
| 基础地址 | `http://<host>:8081`（开发环境前端 5173 端口经 Vite 代理转发） |
| 数据格式 | 请求与响应均为 `application/json; charset=UTF-8` |
| 响应包装 | `{ "success": true/false, ...业务字段, "msg": "失败原因" }` |
| 认证方式 | `Authorization: Bearer <JWT>`（登录接口签发，有效期默认 1440 分钟） |
| 认证范围 | 除 `/api/auth/**`、`/api/public/**` 外的 `/api/**` 均需登录 |
| 管理接口 | `/api/admin/**` 额外要求 `role = ADMIN`，否则返回 `403` |
| 未登录 | 返回 `401` + `{"success":false,"msg":"未登录或token无效"}` |
| 身份传递 | 后端经 JWT 解析后从 `request.attribute("currentUserId")` 获取当前用户 |

## 二、认证模块 `/api/auth`

| 方法 | 路径 | 说明 | 关键参数 | 返回 |
|---|---|---|---|---|
| POST | `/api/auth/login` | 登录，签发 JWT | body: `username`, `password` | `token`, `tokenType`, `user{id,username,nickname,role,...}` |
| POST | `/api/auth/register` | 注册并直接登录 | body: `username`, `password`, `nickname`, `email` | 同上 |
| GET | `/api/auth/token` | Session 会话换 JWT（双通道桥接） | — | 同上 |

## 三、用户与专注核心 `/api`

| 方法 | 路径 | 说明 | 关键参数 | 返回 |
|---|---|---|---|---|
| GET | `/api/user/profile` | 当前用户资料 | — | `user{...}` |
| POST | `/api/profile/update` | 更新昵称/邮箱/头像 | body: `nickname`, `email`, `avatarUrl` | `user{...}` |
| GET | `/api/tasks` | 任务大厅任务列表（仅启用） | — | `items:[{taskName,defaultMinutes,icon,plantName,...}]` |
| POST | `/api/focus/start` | 开始专注（服务端记录时间戳防伪造） | body: `taskName`, `minutes` | `sessionId` |
| POST | `/api/focus/complete` | 完成专注（校验时长、落库、发植物） | body: `sessionId` | `record{...}`, `plant{...}` |
| GET | `/api/history` | 专注历史 | query: `days`（默认 30） | `items:[{taskName,taskCategory,durationMinutes,completedTime}]` |
| GET | `/api/achievements` | 成就列表 | — | `items:[...]` |
| GET | `/api/encyclopedia` | 植物图鉴 | — | `items:[...]` |

## 四、花园模块 `/api/garden`

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| GET | `/api/garden/map` | 我的花园 6×6（可扩建 10×10）地图 | query: `userId`（缺省为当前用户） |
| GET | `/api/garden/pending` | 待种植植物队列 | — |
| POST | `/api/garden/plant` | 种下植物 | body: `recordId`, `x`, `y` |
| POST | `/api/garden/remove` | 移除植物 | body: `x`, `y` |
| POST | `/api/garden/expand` | 扩建花园（6→8→10） | — |

## 五、数据报告 `/api/report`（ECharts 数据源）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/report/trend` | 专注时长趋势（折线图） |
| GET | `/api/report/category` | 任务类别分布（饼图） |
| GET | `/api/report/heatmap` | 专注热力日历（日历热力图） |
| GET | `/api/report/best-hours` | 24 小时分布与黄金专注时段（柱状图） |
| GET | `/api/report/annual` | 年度总结页数据 |

## 六、推荐与社交 `/api`

| 方法 | 路径 | 说明 | 关键参数 | 返回 |
|---|---|---|---|---|
| GET | `/api/recommendations` | 混合推荐 Top-N（可解释） | query: `n`（1-7，默认 5） | `items:[{category,taskName,minutes,score,reason,icon,plantName}]` |
| GET | `/api/friends/search` | 好友搜索 | query: `keyword` | `items:[...]` |
| POST | `/api/friends/like` | 好友花园点赞 | body: `toUserId` | — |
| GET | `/api/rooms` | 协作专注房间列表 | — | `rooms:[...]` |

> WebSocket 房间通信端点：`/ws/focus-room`（`FocusRoomEndpoint`），消息协议见源码注释。

## 七、系统管理 `/api/admin`（要求 ADMIN 角色）

| 方法 | 路径 | 说明 | 关键参数 |
|---|---|---|---|
| POST | `/api/admin/eval/run` | **运行推荐算法消融评估**（论文实验），返回 5 档模型的 P@3/R@3/P@5/R@5 | — |
| GET | `/api/admin/users` | 用户列表 | query: `keyword`（用户名/昵称模糊） |
| PUT | `/api/admin/users/{id}/enabled` | 启用/停用账号 | body: `enabled` |
| PUT | `/api/admin/users/{id}/role` | 调整角色 | body: `role`（`USER`/`ADMIN`） |
| DELETE | `/api/admin/users/{id}` | 删除用户（级联清理记录/花园/成就/点赞） | — |
| GET | `/api/admin/tasks` | 任务列表（含禁用，含推导类别） | — |
| POST | `/api/admin/tasks` | 新增任务 | body: `taskName`, `defaultMinutes`, `plantId`, `icon`, `sortOrder`, `description` |
| PUT | `/api/admin/tasks/{id}` | 修改任务（含启用开关） | 同上 |
| DELETE | `/api/admin/tasks/{id}` | 删除任务 | — |
| GET | `/api/admin/plants` | 植物下拉选项 | — |

## 八、公开接口 `/api`（免认证）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/` | 系统简介（版本、功能开关） |

## 九、错误码汇总

| HTTP 状态 | 含义 | 触发场景 |
|---|---|---|
| 200 | 成功（业务失败看 `success:false` + `msg`） | 正常返回 |
| 401 | 未登录 / JWT 无效或过期 | 未携带 token 访问受保护接口 |
| 403 | 权限不足 | 非 ADMIN 访问 `/api/admin/**` |
| 500 | 服务端异常 | 由 `GlobalExceptionHandler` 统一包装 |
