package com.stargarden.service;

import com.stargarden.entity.TaskRecord;
import com.stargarden.repository.TaskRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐算法离线评估服务（开题报告：验证混合推荐算法相较基础协同过滤算法的效果提升）。
 *
 * 评估协议：留一时间法（Leave-One-Out by Time）——
 *   每个用户按完成时间排序，前 80% 行为作为训练集，后 20% 作为测试集；
 *   测试集中用户实际完成过的类别视为"相关物品"。
 * 消融模型（逐步叠加组件，验证每个因子的贡献）：
 *   POPULAR    全局热门推荐（非个性化基线）
 *   CF         基础 UserCF（余弦相似度，无时间衰减）
 *   CF+DECAY   UserCF + 时间衰减因子
 *   CF+TAG     CF+DECAY + 标签相似度因子
 *   FULL       CF+TAG + 时段匹配因子（完整混合模型）
 * 指标：Precision@N 与 Recall@N（N = 3, 5），对全部测试用户取宏平均。
 */
@Service
public class EvalService {

    @Autowired
    private TaskRecordRepository taskRecordRepository;

    private static final List<String> ALL_CATEGORIES =
            List.of("学习", "阅读", "运动", "早起", "冥想", "工作", "生活");
    private static final int[] N_VALUES = {3, 5};
    /** 训练集比例 */
    private static final double TRAIN_RATIO = 0.8;

    /**
     * 执行消融评估。
     *
     * @return 有序 Map：模型名 -> (指标名 -> 指标值)，模型按消融顺序排列
     */
    public LinkedHashMap<String, Map<String, Double>> evaluate() {
        List<TaskRecord> all = taskRecordRepository.findAll();

        // 1. 按用户划分训练/测试集（留一时间法）
        Map<Long, List<TaskRecord>> byUser = all.stream()
                .filter(r -> r.getCompletedTime() != null)
                .sorted(Comparator.comparing(TaskRecord::getCompletedTime))
                .collect(Collectors.groupingBy(TaskRecord::getUserId,
                        LinkedHashMap::new, Collectors.toList()));

        List<TaskRecord> trainSet = new ArrayList<>();
        Map<Long, Set<String>> testRelevant = new HashMap<>();
        for (Map.Entry<Long, List<TaskRecord>> e : byUser.entrySet()) {
            List<TaskRecord> records = e.getValue();
            int cut = (int) Math.max(1, Math.floor(records.size() * TRAIN_RATIO));
            // 记录数 <= 2 的用户不参与评估（信号不足）
            if (records.size() <= 2) continue;
            trainSet.addAll(records.subList(0, cut));
            Set<String> relevant = records.subList(cut, records.size()).stream()
                    .map(r -> r.getTaskCategory() != null
                            ? r.getTaskCategory() : TaskCategoryUtil.derive(r.getTaskName()))
                    .collect(Collectors.toSet());
            if (!relevant.isEmpty()) {
                testRelevant.put(e.getKey(), relevant);
            }
        }

        // 2. 基于训练集构建各消融模型
        Map<String, Map<Long, Map<String, Double>>> models = new LinkedHashMap<>();
        models.put("POPULAR", popularModel(trainSet));
        models.put("CF", cfModel(trainSet, false));
        models.put("CF+DECAY", cfModel(trainSet, true));
        models.put("CF+TAG", blend(trainSet, false, 0.6, 0.4, 0.0));
        models.put("FULL", blend(trainSet, true, 0.5, 0.3, 0.2));

        // 3. 逐模型计算 Precision@N / Recall@N
        LinkedHashMap<String, Map<String, Double>> results = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Long, Map<String, Double>>> m : models.entrySet()) {
            Map<Long, Map<String, Double>> scores = m.getValue();
            Map<String, Double> metrics = new LinkedHashMap<>();
            for (int n : N_VALUES) {
                double pSum = 0, rSum = 0;
                int users = 0;
                for (Map.Entry<Long, Set<String>> t : testRelevant.entrySet()) {
                    Map<String, Double> userScores = scores.getOrDefault(t.getKey(), Collections.emptyMap());
                    List<String> topN = topN(userScores, n);
                    Set<String> relevant = t.getValue();
                    long hit = topN.stream().filter(relevant::contains).count();
                    pSum += (double) hit / n;
                    rSum += (double) hit / relevant.size();
                    users++;
                }
                metrics.put("P@" + n, users == 0 ? 0.0 : round4(pSum / users));
                metrics.put("R@" + n, users == 0 ? 0.0 : round4(rSum / users));
            }
            metrics.put("评估用户数", (double) testRelevant.size());
            results.put(m.getKey(), metrics);
        }
        return results;
    }

    // ==================== 消融模型实现 ====================

    /** POPULAR：全局类别热度排名（所有用户同序列，非个性化基线） */
    private Map<Long, Map<String, Double>> popularModel(List<TaskRecord> train) {
        Map<String, Double> pop = new HashMap<>();
        for (TaskRecord r : train) {
            pop.merge(category(r), r.getDurationMinutes() == null ? 0.0 : r.getDurationMinutes(), Double::sum);
        }
        Map<Long, Map<String, Double>> model = new HashMap<>();
        for (Long uid : train.stream().map(TaskRecord::getUserId).distinct().collect(Collectors.toList())) {
            model.put(uid, pop);
        }
        return model;
    }

    /** CF / CF+DECAY：UserCF 余弦相似度（withDecay 控制时间衰减因子开关） */
    private Map<Long, Map<String, Double>> cfModel(List<TaskRecord> train, boolean withDecay) {
        return blend(train, withDecay, 1.0, 0.0, 0.0);
    }

    /**
     * 混合模型通用构造器：
     * score = wCf * UserCF(±decay) + wTag * 标签相似度 + wSlot * 时段匹配
     * 权重为 0 的因子直接跳过，实现消融组合。
     */
    private Map<Long, Map<String, Double>> blend(List<TaskRecord> train, boolean withDecay,
                                                 double wCf, double wTag, double wSlot) {
        Map<Long, Map<String, Double>> matrix = buildMatrix(train, withDecay);
        Map<String, double[]> catHour = null; // 时段分布仅 FULL 需要
        if (wSlot > 0) {
            catHour = categoryHourDistribution(train);
        }

        Map<Long, Map<String, Double>> model = new HashMap<>();
        for (Map.Entry<Long, Map<String, Double>> e : matrix.entrySet()) {
            Long uid = e.getKey();
            Map<String, Double> userRatings = e.getValue();

            Map<String, Double> combined = new HashMap<>();
            if (wCf > 0) {
                Map<String, Double> cf = userCfScores(uid, userRatings, matrix);
                for (String c : ALL_CATEGORIES) {
                    combined.merge(c, wCf * cf.getOrDefault(c, 0.0), Double::sum);
                }
            }
            if (wTag > 0) {
                Map<String, Double> tag = tagScores(userTagVector(userRatings));
                for (String c : ALL_CATEGORIES) {
                    combined.merge(c, wTag * tag.getOrDefault(c, 0.0), Double::sum);
                }
            }
            if (wSlot > 0 && catHour != null) {
                Map<String, Double> slot = slotScores(userHourHistogram(train, uid), catHour, -1);
                for (String c : ALL_CATEGORIES) {
                    combined.merge(c, wSlot * slot.getOrDefault(c, 0.0), Double::sum);
                }
            }
            model.put(uid, combined);
        }
        return model;
    }

    // ==================== 评分与相似度（与 RecommendationService 同构，支持传入子集） ====================

    private String category(TaskRecord r) {
        return r.getTaskCategory() != null ? r.getTaskCategory() : TaskCategoryUtil.derive(r.getTaskName());
    }

    /** 构建 用户-类别 评分矩阵（duration 加权，可选时间衰减） */
    private Map<Long, Map<String, Double>> buildMatrix(List<TaskRecord> records, boolean withDecay) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Map<String, Double>> matrix = new HashMap<>();
        for (TaskRecord r : records) {
            double duration = r.getDurationMinutes() == null ? 0 : r.getDurationMinutes();
            if (withDecay && r.getCompletedTime() != null) {
                long daysAgo = Math.max(0, java.time.Duration.between(r.getCompletedTime(), now).toDays());
                duration *= Math.exp(-0.02 * daysAgo);
            }
            matrix.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                    .merge(category(r), duration, Double::sum);
        }
        return matrix;
    }

    /** UserCF Top-10 邻居加权预测，输出归一化得分 */
    private Map<String, Double> userCfScores(Long uid, Map<String, Double> userRatings,
                                             Map<Long, Map<String, Double>> matrix) {
        List<Map.Entry<Long, Double>> neighbors = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> e : matrix.entrySet()) {
            if (e.getKey().equals(uid)) continue;
            double sim = cosine(userRatings, e.getValue());
            if (sim > 0) neighbors.add(Map.entry(e.getKey(), sim));
        }
        neighbors.sort(Map.Entry.<Long, Double>comparingByValue().reversed());
        List<Map.Entry<Long, Double>> topK =
                neighbors.subList(0, Math.min(10, neighbors.size()));
        double simSum = topK.stream().mapToDouble(Map.Entry::getValue).sum();
        if (simSum <= 0) return Collections.emptyMap();
        Map<String, Double> scores = new HashMap<>();
        for (String c : ALL_CATEGORIES) {
            double w = 0;
            for (Map.Entry<Long, Double> nb : topK) {
                w += nb.getValue() * matrix.get(nb.getKey()).getOrDefault(c, 0.0);
            }
            scores.put(c, w / simSum);
        }
        return normalize(scores);
    }

    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Double bv = b.get(e.getKey());
            if (bv != null) dot += e.getValue() * bv;
        }
        for (double v : b.values()) nb += v * v;
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private Map<String, Double> tagScores(Map<String, Double> userTagVec) {
        Map<String, Double> scores = new HashMap<>();
        for (String c : ALL_CATEGORIES) {
            Map<String, Double> catVec = new HashMap<>();
            for (String t : TaskCategoryUtil.CATEGORY_TAGS.getOrDefault(c, List.of())) {
                catVec.put(t, 1.0);
            }
            scores.put(c, cosine(userTagVec, catVec));
        }
        return normalize(scores);
    }

    private Map<String, Double> userTagVector(Map<String, Double> userRatings) {
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Double> e : userRatings.entrySet()) {
            for (String tag : TaskCategoryUtil.CATEGORY_TAGS.getOrDefault(e.getKey(), List.of())) {
                vec.merge(tag, e.getValue(), Double::sum);
            }
        }
        return vec;
    }

    /** 类别 -> 24 小时分布（Laplace 平滑） */
    private Map<String, double[]> categoryHourDistribution(List<TaskRecord> records) {
        Map<String, double[]> dist = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        for (TaskRecord r : records) {
            if (r.getCompletedTime() == null) continue;
            double[] hist = dist.computeIfAbsent(category(r), k -> new double[24]);
            hist[r.getCompletedTime().getHour()] += 1;
            totals.merge(category(r), 1, Integer::sum);
        }
        dist.replaceAll((k, hist) -> {
            double total = Math.max(1, totals.getOrDefault(k, 1));
            for (int h = 0; h < 24; h++) hist[h] = hist[h] / total;
            return hist;
        });
        return dist;
    }

    private double[] userHourHistogram(List<TaskRecord> records, Long uid) {
        double[] hist = new double[24];
        double total = 0;
        for (TaskRecord r : records) {
            if (!r.getUserId().equals(uid) || r.getCompletedTime() == null) continue;
            hist[r.getCompletedTime().getHour()] += 1;
            total += 1;
        }
        if (total > 0) for (int h = 0; h < 24; h++) hist[h] /= total;
        return hist;
    }

    /** 时段匹配得分：小时分布余弦相似度；currentHour < 0 表示评估模式（不做当前时刻加成） */
    private Map<String, Double> slotScores(double[] userHourHist, Map<String, double[]> catHour, int currentHour) {
        Map<String, Double> scores = new HashMap<>();
        for (String c : ALL_CATEGORIES) {
            double[] dist = catHour.getOrDefault(c, new double[24]);
            double sim = cosineArray(userHourHist, dist);
            if (currentHour >= 0) {
                sim = Math.min(1.0, sim + userHourHist[currentHour] * dist[currentHour] * 8.0);
            }
            scores.put(c, sim);
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

    // ==================== 工具方法 ====================

    /** 按得分取 Top-N 类别名列表 */
    private List<String> topN(Map<String, Double> scores, int n) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

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
