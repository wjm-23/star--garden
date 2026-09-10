package com.stargarden.service;

import com.stargarden.dto.RecommendationItem;
import com.stargarden.entity.FocusTask;
import com.stargarden.entity.TaskRecord;
import com.stargarden.repository.TaskRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合推荐服务（开题报告核心技术点）。
 *
 * 评分矩阵：以「任务类别」为物品维度，隐式评分 =
 *          用户对该类别历史专注时长 x 时间衰减因子 exp(-0.02 * 距今天数)。
 * 混合策略：score = 0.5 * UserCF + 0.3 * 标签相似度 + 0.2 * 时段匹配因子
 * 冷启动：行为记录 < 5 条时，score = 0.4 * 全局热度 + 0.3 * 标签相似度 + 0.3 * 时段匹配因子
 *
 * 推荐结果落地为任务大厅中真实可选的专注任务（FocusTask），
 * 并携带可解释 reason，便于前端展示"为什么推荐给你"。
 */
@Service
public class RecommendationService {

    @Autowired
    private TaskRecordRepository taskRecordRepository;

    @Autowired
    private FocusTaskService focusTaskService;

    /** UserCF 邻居数量 K */
    private static final int TOP_K_NEIGHBORS = 10;
    /** 时间衰减半衰系数：约 35 天前记录权重减半 */
    private static final double DECAY_FACTOR = 0.02;
    /** 冷启动阈值：行为记录少于该值走全局热度兜底 */
    private static final int COLD_START_THRESHOLD = 5;
    /** 混合权重 */
    private static final double W_CF = 0.5, W_TAG = 0.3, W_SLOT = 0.2;
    private static final double W_POP_COLD = 0.4, W_TAG_COLD = 0.3, W_SLOT_COLD = 0.3;

    /** 推荐的类别全集（与 TaskCategoryUtil 一致，共 7 类） */
    private static final List<String> ALL_CATEGORIES =
            List.of("学习", "阅读", "运动", "早起", "冥想", "工作", "生活");

    /**
     * 为指定用户生成 Top-N 个性化推荐。
     *
     * @param userId 用户ID
     * @param n      推荐条数
     * @return 推荐列表（按混合得分降序）
     */
    public List<RecommendationItem> recommend(Long userId, int n) {
        List<TaskRecord> all = taskRecordRepository.findAll();
        Map<Long, Map<String, Double>> matrix = buildRatingMatrix(all, true);
        Map<String, Double> globalPop = globalPopularity(all);
        Map<String, double[]> categoryHourDist = categoryHourDistribution(all);

        Map<String, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        int userRecordCount = (int) all.stream().filter(r -> r.getUserId().equals(userId)).count();

        // 用户偏好标签向量 与 用户小时直方图
        Map<String, Double> userTagVec = userTagVector(userRatings);
        double[] userHourHist = userHourHistogram(all, userId);

        boolean coldStart = userRecordCount < COLD_START_THRESHOLD;
        int currentHour = LocalDateTime.now().getHour();

        // 1. 计算每个类别的混合得分
        Map<String, Double> cfScores = coldStart ? Collections.emptyMap()
                : userCfScores(userId, userRatings, matrix);
        Map<String, Double> tagScores = tagScores(userTagVec);
        Map<String, Double> slotScores = slotScores(userHourHist, categoryHourDist, currentHour);

        // 类别 -> 任务大厅中该类别的启用任务（推荐结果必须是大厅里能直接开始的真任务）
        Map<String, List<FocusTask>> tasksByCategory = focusTaskService.listEnabled().stream()
                .collect(Collectors.groupingBy(t -> TaskCategoryUtil.derive(t.getTaskName())));

        List<RecommendationItem> items = new ArrayList<>();
        for (String category : ALL_CATEGORIES) {
            double score;
            String reason;
            if (coldStart) {
                score = W_POP_COLD * globalPop.getOrDefault(category, 0.0)
                        + W_TAG_COLD * tagScores.getOrDefault(category, 0.0)
                        + W_SLOT_COLD * slotScores.getOrDefault(category, 0.0);
                reason = "全站用户近期最常专注的类别之一，适合建立你的专注习惯";
            } else {
                score = W_CF * cfScores.getOrDefault(category, 0.0)
                        + W_TAG * tagScores.getOrDefault(category, 0.0)
                        + W_SLOT * slotScores.getOrDefault(category, 0.0);
                reason = buildReason(category, cfScores, tagScores, slotScores,
                        userRatings, userId, matrix, currentHour);
            }
            RecommendationItem item = new RecommendationItem();
            item.setCategory(category);
            item.setScore(round4(score));
            applyFocusTask(item, tasksByCategory.getOrDefault(category, List.of()), category);
            item.setReason(reason);
            items.add(item);
        }

        // 2. 过滤用户当天已完成的类别，避免重复推荐；按得分取 Top-N
        Set<String> doneToday = all.stream()
                .filter(r -> r.getUserId().equals(userId))
                .filter(r -> r.getCompletedTime() != null
                        && r.getCompletedTime().toLocalDate().equals(LocalDateTime.now().toLocalDate()))
                .map(TaskRecord::getTaskCategory)
                .collect(Collectors.toSet());
        if (doneToday.size() < ALL_CATEGORIES.size() - 1) {
            items.removeIf(it -> doneToday.contains(it.getCategory()));
        }
        items.sort(Comparator.comparingDouble(RecommendationItem::getScore).reversed());
        return items.subList(0, Math.min(n, items.size()));
    }

    // ==================== 评分矩阵 ====================

    /**
     * 构建用户-类别评分矩阵。
     * rating(u,c) = Σ duration * exp(-decay * daysAgo)，daysAgo 为完成时间距今天数。
     *
     * @param withDecay 是否启用时间衰减（消融实验需要关闭衰减的对照模型）
     */
    public Map<Long, Map<String, Double>> buildRatingMatrix(List<TaskRecord> records, boolean withDecay) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Map<String, Double>> matrix = new HashMap<>();
        for (TaskRecord r : records) {
            String category = r.getTaskCategory() != null
                    ? r.getTaskCategory() : TaskCategoryUtil.derive(r.getTaskName());
            double duration = r.getDurationMinutes() == null ? 0 : r.getDurationMinutes();
            double weight = duration;
            if (withDecay && r.getCompletedTime() != null) {
                long daysAgo = Math.max(0, Duration.between(r.getCompletedTime(), now).toDays());
                weight = duration * Math.exp(-DECAY_FACTOR * daysAgo);
            }
            matrix.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                    .merge(category, weight, Double::sum);
        }
        return matrix;
    }

    /** 全局类别热度（评分归一化到 0-1），冷启动兜底使用 */
    private Map<String, Double> globalPopularity(List<TaskRecord> records) {
        Map<String, Double> pop = new HashMap<>();
        for (TaskRecord r : records) {
            String category = r.getTaskCategory() != null
                    ? r.getTaskCategory() : TaskCategoryUtil.derive(r.getTaskName());
            pop.merge(category, r.getDurationMinutes() == null ? 0.0 : r.getDurationMinutes(), Double::sum);
        }
        double max = pop.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        final double maxFinal = max;
        pop.replaceAll((k, v) -> v / maxFinal);
        return pop;
    }

    // ==================== UserCF（余弦相似度） ====================

    /**
     * 基于 UserCF 的类别得分预测：
     * score(u,c) = Σ_{v∈TopK} sim(u,v) * rating(v,c) / Σ_{v∈TopK} sim(u,v)
     */
    private Map<String, Double> userCfScores(Long userId,
                                             Map<String, Double> userRatings,
                                             Map<Long, Map<String, Double>> matrix) {
        // 计算 Top-K 相似邻居
        List<Map.Entry<Long, Double>> neighbors = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> e : matrix.entrySet()) {
            if (e.getKey().equals(userId)) continue;
            double sim = cosine(userRatings, e.getValue());
            if (sim > 0) neighbors.add(Map.entry(e.getKey(), sim));
        }
        neighbors.sort(Map.Entry.<Long, Double>comparingByValue().reversed());
        List<Map.Entry<Long, Double>> topK =
                neighbors.subList(0, Math.min(TOP_K_NEIGHBORS, neighbors.size()));

        double simSum = topK.stream().mapToDouble(Map.Entry::getValue).sum();
        Map<String, Double> scores = new HashMap<>();
        if (simSum <= 0) return scores;
        for (String category : ALL_CATEGORIES) {
            double weighted = 0;
            for (Map.Entry<Long, Double> nb : topK) {
                weighted += nb.getValue() * matrix.get(nb.getKey()).getOrDefault(category, 0.0);
            }
            scores.put(category, weighted / simSum);
        }
        // 归一化到 0-1，便于与标签、时段因子加权融合
        return normalize(scores);
    }

    /** 向量余弦相似度（缺失维度按 0 处理） */
    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Double bv = b.get(e.getKey());
            if (bv != null) dot += e.getValue() * bv;
        }
        for (double v : b.values()) nb += v * v;
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ==================== 标签相似度因子 ====================

    /**
     * 用户偏好标签向量：Σ rating(c) * 标签指示(c,t)，与类别标签向量做余弦相似度。
     */
    private Map<String, Double> tagScores(Map<String, Double> userTagVec) {
        Map<String, Double> scores = new HashMap<>();
        for (String category : ALL_CATEGORIES) {
            List<String> tags = TaskCategoryUtil.CATEGORY_TAGS.getOrDefault(category, List.of());
            Map<String, Double> catVec = new HashMap<>();
            for (String t : tags) catVec.put(t, 1.0);
            scores.put(category, cosine(userTagVec, catVec));
        }
        return normalize(scores);
    }

    /** 用户偏好标签向量（评分加权累加后归一化） */
    private Map<String, Double> userTagVector(Map<String, Double> userRatings) {
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Double> e : userRatings.entrySet()) {
            for (String tag : TaskCategoryUtil.CATEGORY_TAGS.getOrDefault(e.getKey(), List.of())) {
                vec.merge(tag, e.getValue(), Double::sum);
            }
        }
        return vec;
    }

    // ==================== 时段匹配因子 ====================

    /**
     * 时段匹配得分 = 用户小时直方图 与 类别小时分布 的余弦相似度（形状匹配），
     * 度量"该类别的高峰时段是否与用户活跃时段共振"；
     * 并对当前小时（用户此刻最可能专注的时间）额外加成。
     */
    private Map<String, Double> slotScores(double[] userHourHist,
                                           Map<String, double[]> categoryHourDist,
                                           int currentHour) {
        Map<String, Double> scores = new HashMap<>();
        for (String category : ALL_CATEGORIES) {
            double[] dist = categoryHourDist.getOrDefault(category, new double[24]);
            double sim = cosineArray(userHourHist, dist);
            if (currentHour >= 0) {
                double nowAlign = userHourHist[currentHour] * dist[currentHour] * 8.0;
                sim = Math.min(1.0, sim + nowAlign);
            }
            scores.put(category, sim);
        }
        return normalize(scores);
    }

    /** 24 维向量余弦相似度 */
    private double cosineArray(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int h = 0; h < 24; h++) {
            dot += a[h] * b[h];
            na += a[h] * a[h];
            nb += b[h] * b[h];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 类别 -> 24 小时完成概率分布（Laplace 平滑） */
    private Map<String, double[]> categoryHourDistribution(List<TaskRecord> records) {
        Map<String, double[]> dist = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        for (TaskRecord r : records) {
            if (r.getCompletedTime() == null) continue;
            String category = r.getTaskCategory() != null
                    ? r.getTaskCategory() : TaskCategoryUtil.derive(r.getTaskName());
            double[] hist = dist.computeIfAbsent(category, k -> new double[24]);
            hist[r.getCompletedTime().getHour()] += 1;
            totals.merge(category, 1, Integer::sum);
        }
        dist.replaceAll((k, hist) -> {
            double total = Math.max(1, totals.getOrDefault(k, 1));
            for (int h = 0; h < 24; h++) hist[h] = hist[h] / total;
            return hist;
        });
        return dist;
    }

    /** 用户 -> 24 小时行为直方图（归一化为概率） */
    private double[] userHourHistogram(List<TaskRecord> records, Long userId) {
        double[] hist = new double[24];
        double total = 0;
        for (TaskRecord r : records) {
            if (!r.getUserId().equals(userId) || r.getCompletedTime() == null) continue;
            hist[r.getCompletedTime().getHour()] += 1;
            total += 1;
        }
        if (total > 0) {
            for (int h = 0; h < 24; h++) hist[h] /= total;
        }
        return hist;
    }

    // ==================== 可解释推荐 ====================

    /**
     * 生成推荐理由：择优展示相似用户行为 / 标签偏好 / 高效时段三类证据。
     */
    private String buildReason(String category,
                               Map<String, Double> cfScores,
                               Map<String, Double> tagScores,
                               Map<String, Double> slotScores,
                               Map<String, Double> userRatings,
                               Long userId,
                               Map<Long, Map<String, Double>> matrix,
                               int currentHour) {
        double cf = cfScores.getOrDefault(category, 0.0);
        double tag = tagScores.getOrDefault(category, 0.0);
        double slot = slotScores.getOrDefault(category, 0.0);
        double max = Math.max(cf, Math.max(tag, slot));

        if (max == cf && cf > 0) {
            // 找出对该类别评分最高的相似邻居作为佐证
            String topNeighbor = matrix.entrySet().stream()
                    .filter(e -> !e.getKey().equals(userId))
                    .sorted((a, b) -> Double.compare(
                            b.getValue().getOrDefault(category, 0.0),
                            a.getValue().getOrDefault(category, 0.0)))
                    .findFirst()
                    .map(e -> "用户" + e.getKey())
                    .orElse("相似用户");
            double mine = userRatings.getOrDefault(category, 0.0);
            if (mine > 0) {
                return String.format("与你习惯相似的用户也在坚持「%s」，你过去在该类别已累计 %.0f 分钟，继续保持",
                        category, mine);
            }
            return String.format("%s 等与你专注习惯相似的用户常完成「%s」任务，值得一试", topNeighbor, category);
        }
        if (max == tag && tag > 0) {
            List<String> tags = TaskCategoryUtil.CATEGORY_TAGS.getOrDefault(category, List.of());
            return String.format("符合你偏好的「%s」标签，与你的兴趣画像契合度高", String.join("、", tags));
        }
        if (max == slot && slot > 0) {
            return String.format("你在 %d 点前后专注效率最高，「%s」任务此刻完成质量更佳", currentHour, category);
        }
        return "根据你的专注行为分析，该类别与你的整体节奏匹配";
    }

    // ==================== 推荐结果落地为真实任务 ====================

    /**
     * 将类别得分落地为任务大厅中的真实任务：
     * 优先取管理员配置的该类别启用任务（同类多个时按"当天天数+轮换索引"选取，避免每天重复），
     * 大厅暂无该类别任务时回退到内置任务池（完成时由任务名推导类别，链路依然闭环）。
     */
    private void applyFocusTask(RecommendationItem item, List<FocusTask> pool, String category) {
        if (pool != null && !pool.isEmpty()) {
            int idx = Math.abs((LocalDateTime.now().getDayOfYear() + category.hashCode()) % pool.size());
            FocusTask ft = pool.get(idx);
            item.setTaskName(ft.getTaskName());
            item.setMinutes(ft.getDefaultMinutes() == null
                    ? TaskCategoryUtil.CATEGORY_MINUTES.getOrDefault(category, 25) : ft.getDefaultMinutes());
            item.setIcon(ft.getIcon() == null ? "✨" : ft.getIcon());
            item.setPlantName(ft.getPlantName() == null ? "神秘植物" : ft.getPlantName());
            item.setPlantId(ft.getPlantId());
        } else {
            List<String> namePool = TaskCategoryUtil.CATEGORY_TASKS.getOrDefault(category, List.of("自由专注"));
            int idx = Math.abs((LocalDateTime.now().getDayOfYear() + category.hashCode()) % namePool.size());
            item.setTaskName(namePool.get(idx));
            item.setMinutes(TaskCategoryUtil.CATEGORY_MINUTES.getOrDefault(category, 25));
            item.setIcon("✨");
            item.setPlantName("神秘植物");
        }
    }

    /** 归一化到 0-1（min-max，全 0 时返回原值） */
    private Map<String, Double> normalize(Map<String, Double> scores) {
        if (scores.isEmpty()) return scores;
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max <= 0) return scores;
        scores.replaceAll((k, v) -> v / max);
        return scores;
    }

    private double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
