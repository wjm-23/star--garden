# 混合推荐算法与消融实验

论文的核心技术章节，代码在 `service/RecommendationService.java` 与 `service/EvalService.java`。

## 一、问题定义

以**任务类别**为「物品」维度（共 7 类），为每位用户预测其对各类别的偏好得分，取 Top-N 推荐。
隐式反馈来自 `task_record` 表的专注时长，**无显式评分**。

## 二、隐式评分矩阵

```
rating(u, c) = Σ_{r ∈ records(u,c)} duration(r) × exp(-0.02 × daysAgo(r))
```

- `DECAY_FACTOR = 0.02`，即 **约 35 天前的行为权重减半**（`exp(-0.02×35) ≈ 0.5`）
- `daysAgo = max(0, Duration.between(completedTime, now).toDays())`
- 类别缺失时由 `TaskCategoryUtil.derive(taskName)` 推导，兜底 `生活`
- `buildRatingMatrix(records, withDecay)` 的 `withDecay` 开关供**消融实验**产出无衰减对照模型

## 三、三个因子

### 1. UserCF（`W_CF = 0.5`）

```
score_cf(u,c) = Σ_{v ∈ TopK(u)} sim(u,v) × rating(v,c) / Σ_{v ∈ TopK(u)} sim(u,v)
sim(u,v) = cosine(rating_u, rating_v)     // 缺失维度按 0
```

`TOP_K_NEIGHBORS = 10`。输出前先 min-max 归一化到 0–1，才能与另两个因子加权。

### 2. 标签相似度（`W_TAG = 0.3`）

每个类别有一个标签向量（`TaskCategoryUtil.CATEGORY_TAGS`）：

| 类别 | 标签 | 建议时长 |
|---|---|---|
| 学习 | 专注、自我提升、知识 | 45 |
| 阅读 | 专注、放松、知识 | 30 |
| 运动 | 健康、活力、减压 | 30 |
| 早起 | 健康、自律 | 0 |
| 冥想 | 放松、减压、专注 | 10 |
| 工作 | 专注、自我提升、效率 | 45 |
| 生活 | 生活、放松 | 20 |

用户标签向量 = Σ rating(c) × 标签指示(c, t)，再与各类别标签向量求余弦。

### 3. 时段匹配（`W_SLOT = 0.2`）

- 用户侧：24 维小时直方图（归一化为概率）
- 类别侧：该类别 24 小时完成分布，Laplace 平滑（`total = max(1, count)`）
- 得分 = 两分布余弦相似度（形状匹配），**并对当前小时额外加成**：
  `sim = min(1.0, sim + userHist[h] × catDist[h] × 8.0)`

评估模式下 `currentHour = -1`，不做当前时刻加成。

## 四、融合与冷启动

```
常规：score = 0.5 × CF + 0.3 × TAG + 0.2 × SLOT
冷启动（该用户记录 < 5 条）：score = 0.4 × 全局热度 + 0.3 × TAG + 0.3 × SLOT
```

`COLD_START_THRESHOLD = 5`。冷启动时不计算 UserCF（信号不足），改用全站类别热度兜底，理由文案固定为「全站用户近期最常专注的类别之一，适合建立你的专注习惯」。

## 五、结果落地与去重

1. **必须落地为真任务**：`applyFocusTask` 优先从任务大厅取该类别的启用 `FocusTask`；同类多个时按 `|dayOfYear + category.hashCode()| % size` 轮换，避免每天重复；大厅无该类别任务时回退到 `TaskCategoryUtil.CATEGORY_TASKS` 内置池。
2. **过滤当天已完成类别**：若当天已完成的类别数 `< 6`（7-1），则移除这些类别；否则保留（防止全被过滤为空）。
3. 按得分降序取 Top-N。

## 六、可解释推荐

`buildReason` 取三个因子中最大的那个作为主证据，生成中文理由：

- CF 主导 → 「与你习惯相似的用户也在坚持「X」，你过去在该类别已累计 N 分钟，继续保持」或「用户K 等与你专注习惯相似的用户常完成「X」任务」
- TAG 主导 → 「符合你偏好的「标签1、标签2」标签，与你的兴趣画像契合度高」
- SLOT 主导 → 「你在 H 点前后专注效率最高，「X」任务此刻完成质量更佳」
- 兜底 → 「根据你的专注行为分析，该类别与你的整体节奏匹配」

这个 reason 直接展示在任务大厅，**是论文「可解释性」的落点**。

## 七、消融实验（EvalService）

### 评估协议

**按时间的留一法（Leave-One-Out by Time）**：
- 每个用户的行为按 `completedTime` 升序排列
- 前 80%（`TRAIN_RATIO = 0.8`）作训练集，后 20% 作测试集
- 测试集中用户实际完成过的类别视为「相关物品」
- **记录数 ≤ 2 的用户不参与评估**（信号不足）

### 消融梯度

| 模型 | 构成 | 权重 (CF, TAG, SLOT) |
|---|---|---|
| `POPULAR` | 全局热度，非个性化基线 | — |
| `CF` | UserCF，无时间衰减 | (1.0, 0, 0)，decay=off |
| `CF+DECAY` | UserCF + 时间衰减 | (1.0, 0, 0)，decay=on |
| `CF+TAG` | 上者 + 标签相似度 | (0.6, 0.4, 0) |
| `FULL` | 完整混合模型 | (0.5, 0.3, 0.2) |

`blend(train, withDecay, wCf, wTag, wSlot)` 是通用构造器，权重为 0 的因子直接跳过，从而实现消融组合。

### 指标

**Precision@N 与 Recall@N**（`N_VALUES = {3, 5}`），对全部测试用户取宏平均：

```
P@N = |TopN ∩ relevant| / N
R@N = |TopN ∩ relevant| / |relevant|
```

输出为 `LinkedHashMap<模型名, Map<指标名, 值>>`，指标键：`P@3` `R@3` `P@5` `R@5` `评估用户数`。结果保留 4 位小数。

### 跑出有效实验结果的前提

1. `app.data.seed=true`（默认开启），保证有 60 个模拟用户、90 天行为
2. `DataSeeder` 已执行过（首次启动自动执行）
3. 若 `评估用户数` 为 0 或指标全 0，先检查 `task_record` 表是否有数据：

```sql
SELECT user_id, COUNT(*) FROM task_record GROUP BY user_id HAVING COUNT(*) > 2;
```

## 八、调参与改动建议

| 想调整什么 | 改哪里 |
|---|---|
| 三个因子权重 | `RecommendationService` 的 `W_CF` / `W_TAG` / `W_SLOT`；**改完记得同步 `EvalService.blend` 的 FULL 档** |
| 邻居数 K | `TOP_K_NEIGHBORS` |
| 衰减快慢 | `DECAY_FACTOR`（0.02 ≈ 35 天半衰；调大 = 更看重近期） |
| 冷启动阈值 | `COLD_START_THRESHOLD` |
| 增删类别 | 三处同步：`ALL_CATEGORIES`（两个 Service 各有一份）、`TaskCategoryUtil` 的 TAGS / TASKS / MINUTES、`derive()` 关键词 |
| 训练/测试划分 | `EvalService.TRAIN_RATIO` |

**常见坑**：`RecommendationService` 与 `EvalService` 各维护了一份 `ALL_CATEGORIES` 和一份相似度实现（刻意保持同构，避免评估与线上不一致）。改算法时**两边都要改**，否则消融实验的结论对不上线上行为。
