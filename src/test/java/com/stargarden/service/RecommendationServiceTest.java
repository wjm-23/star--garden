package com.stargarden.service;

import com.stargarden.dto.RecommendationItem;
import com.stargarden.entity.FocusTask;
import com.stargarden.entity.TaskRecord;
import com.stargarden.repository.TaskRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 混合推荐算法单元测试（论文第五章：算法正确性验证）。
 * 不依赖数据库，TaskRecordRepository / FocusTaskService 均为 Mockito 桩。
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private TaskRecordRepository taskRecordRepository;

    @Mock
    private FocusTaskService focusTaskService;

    @InjectMocks
    private RecommendationService service;

    private static final List<String> CATEGORIES =
            List.of("学习", "阅读", "运动", "早起", "冥想", "工作", "生活");

    @BeforeEach
    void stubTasks() {
        // 任务大厅：每个类别提供一个启用任务，保证推荐结果可落地
        List<FocusTask> tasks = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            FocusTask t = new FocusTask();
            t.setTaskName(CATEGORIES.get(i) + "任务");
            t.setDefaultMinutes(25);
            t.setIcon("✨");
            t.setPlantName("测试植物");
            t.setSortOrder(i);
            t.setEnabled(true);
            tasks.add(t);
        }
        // 衰减测试不调用推荐主流程，用 lenient 避免严格桩报 UnnecessaryStubbing
        lenient().when(focusTaskService.listEnabled()).thenReturn(tasks);
    }

    private TaskRecord record(long userId, String category, int minutes, int daysAgo) {
        TaskRecord r = new TaskRecord();
        r.setUserId(userId);
        r.setTaskName(category + "任务");
        r.setTaskCategory(category);
        r.setDurationMinutes(minutes);
        // 用昨天/更早的完成时间，避开"当天已完成类别"过滤逻辑
        r.setCompletedTime(LocalDateTime.now().minusDays(daysAgo).withHour(10));
        return r;
    }

    @Test
    @DisplayName("评分矩阵：时间衰减使近期行为权重高于远期行为")
    void ratingMatrixAppliesTimeDecay() {
        // 完成时间精确取 now / now-40天-5分钟，保证 toDays() 落在 0 与 40 上不抖动
        TaskRecord recentR = record(1L, "学习", 60, 1);
        recentR.setCompletedTime(LocalDateTime.now());
        TaskRecord oldR = record(1L, "阅读", 60, 1);
        oldR.setCompletedTime(LocalDateTime.now().minusDays(40).minusMinutes(5));
        List<TaskRecord> records = List.of(recentR, oldR);

        Map<Long, Map<String, Double>> withDecay = service.buildRatingMatrix(records, true);
        double recent = withDecay.get(1L).get("学习");
        double old = withDecay.get(1L).get("阅读");
        // 40 天衰减后权重 = 60 * exp(-0.02*40) ≈ 26.9，近期记录应明显高于远期
        assertEquals(60.0, recent, 1e-9);
        assertEquals(60.0 * Math.exp(-0.02 * 40), old, 1e-6);
        assertTrue(recent > old);

        Map<Long, Map<String, Double>> withoutDecay = service.buildRatingMatrix(records, false);
        assertEquals(60.0, withoutDecay.get(1L).get("学习"), 1e-9);
        assertEquals(60.0, withoutDecay.get(1L).get("阅读"), 1e-9);
    }

    @Test
    @DisplayName("冷启动：行为记录 < 5 条时不抛异常，返回可执行推荐且带理由")
    void coldStartReturnsExplainableItems() {
        List<TaskRecord> records = new ArrayList<>();
        records.add(record(1L, "学习", 30, 1));   // 目标用户仅 3 条 → 冷启动分支
        records.add(record(1L, "阅读", 25, 2));
        records.add(record(1L, "学习", 40, 3));
        for (long u = 2; u <= 6; u++) {            // 其他用户提供全局热度
            for (int i = 0; i < 6; i++) records.add(record(u, "运动", 30, i % 5 + 1));
        }
        when(taskRecordRepository.findAll()).thenReturn(records);

        List<RecommendationItem> items = service.recommend(1L, 5);

        assertTrue(items.size() <= 5);
        for (RecommendationItem it : items) {
            assertNotNull(it.getTaskName(), "推荐必须落地为任务大厅的真实任务");
            assertNotNull(it.getReason(), "每条推荐应携带可解释 reason");
            assertTrue(it.getScore() >= 0);
        }
    }

    @Test
    @DisplayName("个性化：历史集中于「学习」的用户，Top-1 推荐类别应为「学习」")
    void personalizedTopCategoryMatchesUserHistory() {
        List<TaskRecord> records = new ArrayList<>();
        // 目标用户与 5 个相似用户：历史全部集中在「学习」
        for (long u = 1; u <= 6; u++) {
            for (int i = 0; i < 10; i++) records.add(record(u, "学习", 50, i % 10 + 1));
        }
        // 干扰用户：历史集中在「运动」（全局热度高于学习，验证非热度主导）
        for (long u = 7; u <= 16; u++) {
            for (int i = 0; i < 10; i++) records.add(record(u, "运动", 80, i % 10 + 1));
        }
        when(taskRecordRepository.findAll()).thenReturn(records);

        List<RecommendationItem> items = service.recommend(1L, 3);

        assertEquals(3, items.size());
        // UserCF 近邻（其余 5 个学习用户）+ 标签 + 时段三因子均指向学习
        assertEquals("学习", items.get(0).getCategory(),
                "Top-1 应命中用户长期专注的类别，而不是全局热度最高的运动");
    }

    @Test
    @DisplayName("当日已完成类别被过滤，除非几乎全部类别都已完成")
    void doneTodayCategoryFiltered() {
        List<TaskRecord> records = new ArrayList<>();
        for (int i = 0; i < 8; i++) records.add(record(1L, "学习", 40, i + 2));
        // 今天刚完成一次阅读 → 推荐结果不应再包含阅读
        records.add(record(1L, "阅读", 30, 0));
        when(taskRecordRepository.findAll()).thenReturn(records);

        List<RecommendationItem> items = service.recommend(1L, 7);

        assertTrue(items.stream().noneMatch(it -> "阅读".equals(it.getCategory())),
                "当天已完成的类别应从推荐中过滤");
    }
}
