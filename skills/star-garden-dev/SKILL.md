---
name: star-garden-dev
description: star-garden（星芽花园）毕业设计项目的开发助手。当用户提到 star-garden / 星芽花园 / 专注花园 / 毕业论文项目，需要读改代码、调推荐算法、跑消融实验、启停服务、查 H2/MySQL 数据、改前端页面时使用。覆盖 Spring Boot 3.5 + Thymeleaf + JPA 架构、混合推荐算法（UserCF + 标签相似度 + 时段匹配）、Precision@N / Recall@N 消融评估、构建运行与数据播种。
agent_created: true
---

# star-garden 开发助手

专注学习 + 虚拟种植花园的 Web 应用，本科毕业设计项目。核心技术点是**混合推荐算法**及其**消融实验评估**——论文的主要创新点都在这两个 Service 里。

## 项目坐标

- **本地路径**：`C:/Users/86158/Desktop/毕业论文/202339170206 wjm`
- **包名**：`com.stargarden`（注意：脚手架原包名 `com.health.health-system` 不合法，已改）
- **服务端口**：`8081`，context path `/`
- **管理员账号**：`admin`（由 DataSeeder 首次启动创建）

## 技术栈

| 组件 | 版本 / 选型 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.14 |
| 视图 | Thymeleaf（`spring.thymeleaf.cache=false`，改模板无需重启） |
| 持久层 | Spring Data JPA / Hibernate，`ddl-auto=update` |
| 数据库 | 默认 MySQL 8（库名 `star_garden`）；`h2` profile 走文件库 H2 |
| 安全 | BCrypt（`spring-security-crypto`）+ JWT（jjwt 0.12.6） |
| 实时 | `spring-boot-starter-websocket`，原生 `@ServerEndpoint` |
| 图表 | ECharts（`static/lib/echarts.min.js`，本地内置） |
| 其他 | Lombok、Validation |

## 常用命令

在项目根目录执行（Windows 用 `mvnw.cmd`，Git Bash 用 `./mvnw`）：

```bash
# 编译（跳过测试）
./mvnw -q clean compile

# 完整打包
./mvnw -q clean package -DskipTests

# 启动：MySQL 模式（默认，需本地 MySQL 已建库 star_garden）
./mvnw spring-boot:run

# 启动：H2 免安装演示模式（推荐，答辩/演示用）
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# 直接跑已打包的 jar
java -jar target/star-garden-0.0.1-SNAPSHOT.jar --spring.profiles.active=h2
```

启动成功标志：日志出现 `Tomcat started on port 8081` 与 `Started StarGardenApplication`。
H2 控制台：`http://localhost:8081/h2-console`，JDBC URL `jdbc:h2:file:./data/star_garden`，用户 `sa`，密码空。

## 架构速览

```
src/main/java/com/stargarden/
├── StarGardenApplication.java
├── config/       # AuthInterceptor 会话拦截 / JwtInterceptor API 鉴权 / AdminInterceptor
│                 # JwtUtil签发校验 / SecurityConfig(BCrypt) / WebMvcConfig / WebSocketConfig
│                 # GlobalExceptionHandler / SpringContextHolder
├── controller/   # 页面控制器(Thymeleaf) + @RestController(JSON API)
├── service/      # GardenService 业务主流程 / RecommendationService 推荐引擎
│                 # EvalService 离线消融评估 / UserService / FocusTaskService
│                 # DataSeeder 数据播种 / TaskCategoryUtil 类别词典
├── repository/   # Spring Data JPA 接口
├── entity/       # 7 张表
├── dto/          # RecommendationItem
├── enums/        # PlantTaskType
└── ws/           # FocusRoomEndpoint 协作专注房间
```

**两套控制器并存**，改动前先分清：

- `AuthController` / `GardenController` / `IndexController` / `AdminController` —— `@Controller`，返回 Thymeleaf 模板，靠 Session 鉴权
- `AuthApiController` / `RecommendationController` / `ReportApiController` / `RoomApiController` —— `@RestController`，返回 JSON，靠 Session 或 JWT 双通道（`/api/auth/**` 放行）

拦截规则（`WebMvcConfig`）：`AuthInterceptor` 拦 `/**` 但放行首页、登录注册、静态资源与 `/api/**`、`/ws/**`；`JwtInterceptor` 只管 `/api/**`；`AdminInterceptor` 只管 `/admin/**`。

## 数据模型（7 张表）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `users` | username, nickname, password(BCrypt), role, gardenSize=6, consecutiveDays, totalPlants | `gardenSize` 为花园网格边长，默认 6×6 可扩建到 10×10 |
| `plant` | name, description, imageUrl, rarity, icon | 植物图鉴 |
| `focus_task` | taskName, defaultMinutes=25, plantId, plantName, icon, sortOrder, enabled | 任务大厅可选项，管理员在 `/admin/tasks` 配 |
| `task_record` | userId, taskName, **taskCategory**, durationMinutes, completedTime, plantId, planted | 推荐算法与数据报告的**唯一数据源** |
| `user_garden` | userId, plantId, positionX, positionY, plantTime | 花园格子 |
| `achievement` | userId, achievementType, achievedTime | |
| `friend_like` | fromUserId, toUserId, gardenViewTime | |

`task_record.taskCategory` 是推荐算法的核心维度，7 个取值：**学习 / 阅读 / 运动 / 早起 / 冥想 / 工作 / 生活**。写入时若为空，由 `TaskCategoryUtil.derive(taskName)` 按关键词推导，兜底为 `生活`。

## 核心：混合推荐算法

位于 `service/RecommendationService.java`，**论文核心章节**。详细公式、参数与调参方法见 `references/recommendation-algorithm.md`。

一句话概括：

```
score(u,c) = 0.5 × UserCF(余弦, 带时间衰减) + 0.3 × 标签相似度 + 0.2 × 时段匹配
冷启动(记录 < 5 条) = 0.4 × 全局热度 + 0.3 × 标签相似度 + 0.3 × 时段匹配
```

隐式评分定义为 `rating(u,c) = Σ duration × exp(-0.02 × 距今天数)`（约 35 天权重减半）。
推荐结果会**落地成任务大厅里真实存在的 `FocusTask`**，并生成可解释的 `reason` 文案——这两点是论文可写之处，改动时别破坏。

## 消融实验（论文实验章节）

`service/EvalService.java`，管理员页面 `/admin/eval` 触发。评估协议是**按时间的留一法**：每用户行为按完成时间排序，前 80% 训练、后 20% 测试；记录数 ≤ 2 的用户不参与。

消融梯度 5 档：`POPULAR` → `CF` → `CF+DECAY` → `CF+TAG` → `FULL`，指标为 **P@3 / P@5 / R@3 / R@5** 宏平均。

**论文要出图出表时，先确认数据播种已开启**（见下）。没有模拟数据，评估用户数会是 0，指标全为 0。

## 数据播种

`service/DataSeeder.java` 在应用启动时执行：

- 生成 **60 个模拟用户**（`sim01`~`sim60`）、回溯 **90 天**行为
- 随机种子固定为 `20233917L`（学号），保证实验可复现
- 模拟作息分 4 个时段，且时段与类别**刻意相关**（早鸟→早起/运动，晚间→阅读/冥想），这是时段匹配因子的前提假设，别改成随机
- 开关：`app.data.seed`（默认 `true`）
- 幂等：以「`sim01` 是否存在」为播种条件，**不会清空真实用户的历史记录**，可放心重启

## 前端与页面

模板在 `src/main/resources/templates/`，`admin/` 下 5 个后台页（users / plants / plant_form / tasks / eval）。
主页面：index、tasks、garden、encyclopedia、history、achievements、friends、profile、report、annual、rooms、login、register。
样式与脚本集中在一对文件：`static/css/stargarden.css`、`static/js/stargarden.js`。

## 改动时的约定与坑

1. **改 Thymeleaf 模板不用重启**，但改 `static/**` 静态资源若被浏览器缓存，强刷即可。
2. **改实体类字段**靠 `ddl-auto=update` 自动加列，但**不会删列也不会改类型**。需要重构表结构时，H2 模式直接删 `data/star_garden.mv.db` 重启重建；MySQL 模式手工 `ALTER`。
3. **`ddl-auto=update` + `data.sql`**：配置里 `spring.jpa.defer-datasource-initialization=true` 保证建表后再执行 `data.sql`，`spring.sql.init.continue-on-error=true` 让重复启动不报错。
4. **WebSocket 端点用 `@ServerEndpoint`**，不走 Spring MVC 拦截器，所以 `WebMvcConfig` 显式放行 `/ws/**`；端点内取 Bean 要用 `SpringContextHolder`，不能直接 `@Autowired`。
5. **专注时长以服务端计时为准**：`FocusRoomEndpoint` 存 `FOCUS_START` 时间戳防客户端伪造，改动时保留这个约束。
6. **推荐结果必须是可执行的真任务**：`applyFocusTask` 优先取大厅里该类别的启用任务，取不到才回退到 `TaskCategoryUtil.CATEGORY_TASKS` 内置池。新增类别时三个 Map（TAGS / TASKS / MINUTES）要同步补齐。
7. MySQL 连接密码等敏感配置写在本机 `application.properties`，**不要提交到公开仓库**。

## 参考文件

- `references/architecture.md` —— 完整路由表、实体字段、模块职责
- `references/recommendation-algorithm.md` —— 推荐算法公式、参数、调参与消融实验细节
- `references/dev-workflow.md` —— 构建运行、数据库切换、调试与常见故障排查
