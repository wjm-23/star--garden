# 星花园 star-garden

> 专注学习 + 虚拟种植花园 Web 应用 · 本科毕业设计

把"专注计时"和"种花"结合起来：用户每完成一次专注任务，花园里就长出一株植物。系统在此之上内置了一套**混合推荐算法**，根据用户历史行为推荐下一步该做什么任务，并配套**消融实验评估模块**验证各因子的贡献。

---

## 一、项目背景

市面上的专注类应用（Forest、番茄钟等）普遍只解决"计时"问题，缺少对"接下来该做什么"的决策支持。本项目在专注计时的基础上引入推荐系统：

- **行为即数据**：每一次专注记录（任务名、类别、时长、完成时间）都是隐式反馈
- **推荐驱动行为**：系统基于历史行为推荐任务，降低用户的决策成本
- **可解释**：每条推荐都附一句 `reason` 说明"为什么推这个"

---

## 二、技术栈

| 组件 | 选型 |
|---|---|
| JDK | 17 |
| 框架 | Spring Boot 3.5 |
| 视图 | Thymeleaf |
| 持久层 | Spring Data JPA / Hibernate |
| 数据库 | MySQL 8（默认） / H2 文件库（`h2` profile，免安装演示） |
| 安全 | Spring Security Crypto（BCrypt） + JWT（jjwt 0.12.6） |
| 实时通信 | Spring WebSocket（原生 `@ServerEndpoint`） |
| 图表 | ECharts（本地内置） |
| 其他 | Lombok、Jakarta Validation |

---

## 三、核心功能

### 1. 专注与种植
- 番茄钟式专注计时，**服务端记录开始时间戳**，防客户端伪造时长
- 完成后在 6×6 花园格中种下对应植物（可扩建至 10×10）
- 连续打卡天数、成就系统、好友花园互访与点赞

### 2. 协作专注房间
- 基于 WebSocket 的多人房间（`FocusRoomEndpoint`）
- 房间内成员状态实时同步，共同计时

### 3. 混合推荐算法 ⭐
> 论文核心，详见 `src/main/java/com/stargarden/service/RecommendationService.java`

```
score(u, c) = 0.5 × UserCF(余弦相似度, 带时间衰减)
            + 0.3 × 标签相似度
            + 0.2 × 时段匹配

冷启动（用户记录 < 5 条）：
score(u, c) = 0.4 × 全局热度
            + 0.3 × 标签相似度
            + 0.3 × 时段匹配
```

**隐式评分**：`rating(u, c) = Σ duration × exp(-0.02 × 距今天数)`，约 35 天权重减半。

**任务类别**（7 类）：学习 / 阅读 / 运动 / 早起 / 冥想 / 工作 / 生活。写入记录时若类别为空，由 `TaskCategoryUtil.derive(taskName)` 按关键词推导。

**关键设计**：推荐结果会落地成任务大厅里**真实存在的** `FocusTask`，而不是凭空生成的建议——保证推荐可执行。

### 4. 消融实验评估 ⭐
> 论文实验章节，详见 `src/main/java/com/stargarden/service/EvalService.java`

- **评估协议**：按时间的留一法。每用户行为按完成时间排序，前 80% 训练、后 20% 测试；记录数 ≤ 2 的用户不参与
- **消融梯度**（5 档）：

  | 档位 | 说明 |
  |---|---|
  | `POPULAR` | 仅全局热度（基线） |
  | `CF` | 仅 UserCF |
  | `CF+DECAY` | UserCF + 时间衰减 |
  | `CF+TAG` | UserCF + 标签相似度 |
  | `FULL` | 完整混合模型 |

- **指标**：Precision@3 / Precision@5 / Recall@3 / Recall@5（宏平均）
- **入口**：管理员页面 `/admin/eval`

### 5. 数据看板
- 个人数据报告（ECharts 可视化）：类别分布、时长趋势、时段热力
- 年度总结页

---

## 四、快速开始

### 方式一：H2 免安装（推荐，演示/答辩用）

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
# Windows CMD: mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2
```

### 方式二：MySQL

1. 创建数据库：
   ```sql
   CREATE DATABASE star_garden DEFAULT CHARACTER SET utf8mb4;
   ```
2. 复制配置模板并填入自己的连接信息：
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   ```
3. 启动：
   ```bash
   ./mvnw spring-boot:run
   ```

启动成功标志：日志出现 `Tomcat started on port 8081` 与 `Started StarGardenApplication`。

- 访问地址：<http://localhost:8081>
- 管理员账号：`admin`（首次启动由 `DataSeeder` 创建）
- H2 控制台：<http://localhost:8081/h2-console>，JDBC URL `jdbc:h2:file:./data/star_garden`，用户 `sa`，密码空

### 构建打包

```bash
./mvnw clean package -DskipTests
java -jar target/star-garden-0.0.1-SNAPSHOT.jar --spring.profiles.active=h2
```

---

## 五、数据播种（实验可复现）

`DataSeeder` 在应用启动时自动执行：

- 生成 **60 个模拟用户**（`sim01`~`sim60`），回溯 **90 天**行为
- **随机种子固定**为 `20233917L`，保证每次实验结果一致
- 模拟作息分 4 个时段，且**时段与类别刻意相关**（早鸟→早起/运动，晚间→阅读/冥想），这是"时段匹配"因子的前提假设
- 开关：`app.data.seed`（默认 `true`）
- 幂等：以「`sim01` 是否存在」为播种条件，**不会清空真实用户数据**

> ⚠️ 跑消融实验前请确认播种已开启，否则评估用户数为 0，指标全为 0。

---

## 六、项目结构

```
src/main/java/com/stargarden/
├── StarGardenApplication.java
├── config/       # 拦截器(Auth/Jwt/Admin)、JwtUtil、SecurityConfig、
│                 # WebMvcConfig、WebSocketConfig、全局异常处理
├── controller/   # @Controller(Thymeleaf 页面) + @RestController(JSON API)
├── service/      # GardenService 主流程 / RecommendationService 推荐引擎
│                 # EvalService 消融评估 / UserService / FocusTaskService
│                 # DataSeeder 数据播种 / TaskCategoryUtil 类别词典
├── repository/   # Spring Data JPA
├── entity/       # 7 张表：users, plant, focus_task, task_record,
│                 # user_garden, achievement, friend_like
├── dto/          # RecommendationItem
├── enums/        # PlantTaskType
└── ws/           # FocusRoomEndpoint 协作专注房间
```

`task_record` 是推荐算法与数据报告的**唯一数据源**。

---

## 七、数据模型

| 表 | 关键字段 | 说明 |
|---|---|---|
| `users` | username, nickname, password(BCrypt), role, gardenSize, consecutiveDays | 花园默认 6×6，可扩建至 10×10 |
| `plant` | name, description, imageUrl, rarity, icon | 植物图鉴 |
| `focus_task` | taskName, defaultMinutes, plantId, taskCategory, enabled | 任务大厅可选项 |
| `task_record` | userId, taskName, **taskCategory**, durationMinutes, completedTime | 算法数据源 |
| `user_garden` | userId, plantId, positionX, positionY, plantTime | 花园格子 |
| `achievement` | userId, achievementType, achievedTime | 成就 |
| `friend_like` | fromUserId, toUserId, gardenViewTime | 好友互动 |

---

## 八、配套开发助手

仓库内 `skills/star-garden-dev/` 是本项目的 WorkBuddy Skill，包含：

- `SKILL.md` —— 项目坐标、技术栈、常用命令、改动约定
- `references/architecture.md` —— 完整路由表、实体字段、模块职责、鉴权链路
- `references/recommendation-algorithm.md` —— 算法公式、参数含义、调参指南
- `references/dev-workflow.md` —— 构建运行、数据库切换、故障排查

---

## 九、说明

- 本项目为本科毕业设计，侧重算法设计与实验验证，未做生产级性能优化与高并发处理
- `src/main/resources/application.properties` 含数据库密码与 JWT 密钥，**已加入 `.gitignore`**；仓库中提供的是 `application.properties.example` 脱敏模板
