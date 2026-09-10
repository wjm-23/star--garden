# star-garden 架构参考

## 完整路由表

### 页面路由（`@Controller`，返回 Thymeleaf 模板，Session 鉴权）

| 方法 | 路径 | 模板 | 说明 |
|---|---|---|---|
| GET | `/index` | index | 首页 |
| GET | `/login` / POST `/login` | login | 登录页 / 登录提交 |
| GET | `/register` / POST `/register` | register | 注册页 / 注册提交 |
| GET | `/logout` | → login | 退出 |
| GET | `/tasks` | tasks | 任务大厅（推荐位在此） |
| POST | `/task/start` | JSON | 开始专注 |
| POST | `/task/cancel` | JSON | 取消专注 |
| POST | `/task/complete` | JSON | 完成专注，落 `task_record` 并得植物 |
| GET | `/garden` | garden | 我的花园 |
| POST | `/garden/plant` | JSON | 在 (x,y) 种下 |
| POST | `/garden/remove` | JSON | 移除 (x,y) |
| POST | `/garden/expand` | JSON | 扩建花园（6×6 → 上限 10×10） |
| GET | `/encyclopedia` | encyclopedia | 植物图鉴 |
| GET | `/history` | history | 专注历史 |
| GET | `/achievements` | achievements | 成就 |
| GET | `/friends` / GET `/friends/search` / POST `/friends/like` | friends | 好友、搜索、点赞 |
| GET | `/profile` / POST `/profile/update` | profile | 个人资料 |
| GET | `/report` | report | 数据报告（ECharts） |
| GET | `/annual` | annual | 年度报告 |
| GET | `/rooms` | rooms | 协作专注房间 |

### 后台路由（`/admin/**`，需 `role=ADMIN`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users` | 用户管理 |
| GET | `/admin/user/toggle?id=` | 启用 / 禁用 |
| GET | `/admin/plants`、`/admin/plant/add`、`/admin/plant/edit?id=` | 植物图鉴管理 |
| POST | `/admin/plant/save` | 保存植物 |
| GET | `/admin/plant/delete?id=` | 删除植物 |
| GET | `/admin/tasks` | 任务大厅配置 |
| POST | `/admin/tasks/save` | 保存专注任务 |
| GET | `/admin/tasks/delete/{id}` | 删除任务 |
| GET | `/admin/eval` | **推荐算法消融实验看板** |

### JSON API（`@RestController`，Session 或 JWT 双通道）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录换 JWT |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/auth/token` | 用当前 Session 换取 JWT |
| GET | `/api/recommendations?n=` | Top-N 推荐（默认 5） |
| GET | `/api/report/trend?days=` | 趋势（默认 30 天） |
| GET | `/api/report/category` | 类别分布 |
| GET | `/api/report/heatmap` | 热力图 |
| GET | `/api/report/best-hours` | 高效时段 |
| GET | `/api/report/annual` | 年度报告数据 |
| GET | `/api/rooms` | 房间列表 |

### WebSocket

端点 `/ws/room/{roomId}?uid=&uname=`，实现在 `ws/FocusRoomEndpoint.java`。

上行消息：`{"type":"focus-start","minutes":25}` / `{"type":"focus-end"}` / `{"type":"chat","msg":"..."}` / `{"type":"pk"}`
下行消息：`{"type":"system"|"focus-start"|"focus-end"|"chat"|"pk", ..., "members":[{name, focusing}]}`

服务端用四个 `ConcurrentHashMap` 维护状态：`ROOMS`（房间→会话集）、`MEMBERS`（会话→{uid,uname}）、`FOCUSING`、`FOCUS_START`（专注开始时间戳，服务端计时）。

## 实体字段详情

### User（`users`）
`id` / `username` / `nickname` / `email` / `password`(BCrypt) / `avatarUrl` / `role`(默认 `USER`) / `enabled` / `consecutiveDays` / `totalPlants` / `createTime` / `gardenSize`(默认 6，花园网格边长，可扩建至 10)

### Plant（`plant`）
`id` / `name` / `description` / `imageUrl` / `rarity` / `icon`

### FocusTask（`focus_task`）
`id` / `taskName` / `description` / `defaultMinutes`(默认 25) / `plantId` / `plantName`(冗余，便于展示) / `icon`(emoji) / `sortOrder` / `enabled`

### TaskRecord（`task_record`）—— 推荐算法唯一输入
`id` / `userId` / `taskName` / `taskCategory`(7 类) / `durationMinutes` / `completedTime` / `plantId` / `planted`(默认 false)

### UserGarden（`user_garden`）
`id` / `userId` / `plantId` / `positionX` / `positionY` / `plantTime`

### Achievement（`achievement`）
`id` / `userId` / `achievementType` / `achievedTime`

### FriendLike（`friend_like`）
`id` / `fromUserId` / `toUserId` / `gardenViewTime`

## Repository 关键查询

```java
TaskRecordRepository
  findByUserIdOrderByCompletedTimeAsc/Desc(Long userId)
  findByUserIdAndCompletedTimeBetween(userId, start, end)
  findByUserIdAndPlantedOrderByCompletedTimeAsc(userId, planted)   // 待种植队列
  @Query 求和：某用户总专注分钟

PlantRepository
  @Query("SELECT * FROM plant ORDER BY RAND() LIMIT 1", nativeQuery = true)  // 随机掉植物
  findByRarity / findByName

FocusTaskRepository
  findByEnabledTrueOrderBySortOrderAsc / findAllByOrderBySortOrderAsc

UserGardenRepository  findByUserId
AchievementRepository findByUserId
UserRepository        findByUsername / findByEmail
```

## 模块职责

| 类 | 职责 |
|---|---|
| `GardenService` | 业务主流程：完成任务掉植物、种/移除/扩建花园、待种队列、花园地图、成就、近期任务 |
| `RecommendationService` | 混合推荐引擎（论文核心） |
| `EvalService` | 离线消融评估（论文实验） |
| `TaskCategoryUtil` | 类别词典：TAGS / TASKS / MINUTES 三个 Map + `derive(taskName)` 关键词推导 |
| `FocusTaskService` | 任务大厅 CRUD + `initSeed()` 初始任务 |
| `UserService` | 注册（BCrypt 加密）、登录校验、查询 |
| `DataSeeder` | 启动时播种植物图鉴、管理员、模拟行为数据 |
| `JwtUtil` | HS256 签发 / 校验，subject 存 userId，带 username 与 role |
| `SpringContextHolder` | 让 `@ServerEndpoint`（非 Spring 管理）拿到 Bean |

## 认证与鉴权链路

1. **页面**：`AuthInterceptor` 拦 `/**`，放行 `/`、`/index`、`/login`、`/register`、静态资源、`/api/**`、`/ws/**`、`/h2-console/**`。未登录跳登录页。
2. **API**：`JwtInterceptor` 拦 `/api/**`，放行 `/api/auth/**`。支持 **Session 或 JWT 双通道**——有 Session 就用 Session，否则解析 `Authorization` 头。
3. **后台**：`AdminInterceptor` 拦 `/admin/**`，校验 `role == ADMIN`。
4. 密码全程 BCrypt，`SecurityConfig` 提供 `BCryptPasswordEncoder` Bean。
