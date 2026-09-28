package com.stargarden.service;

import com.stargarden.entity.TaskRecord;
import com.stargarden.repository.TaskRecordRepository;
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
import static org.mockito.Mockito.when;

/**
 * 消融评估协议单元测试（论文实验章节的方法学验证）。
 *
 * 构造三组"噪音用户"（运动/冥想/阅读，全局热度前三）与一组"学习用户"：
 * 留一时间法下，POPULAR 基线 Top-3 被三个高热度类别占据，无法命中学习用户的
 * 测试期类别；而完整混合模型（FULL）通过 UserCF 找到学习型近邻，可以命中。
 * 据此断言 FULL 的 Precision@3 / Recall@3 严格优于 POPULAR。
 */
@ExtendWith(MockitoExtension.class)
class EvalServiceTest {

    @Mock
    private TaskRecordRepository taskRecordRepository;

    @InjectMocks
    private EvalService evalService;

    private TaskRecord rec(long userId, String category, int minutes, LocalDateTime time) {
        TaskRecord r = new TaskRecord();
        r.setUserId(userId);
        r.setTaskName(category + "任务");
        r.setTaskCategory(category);
        r.setDurationMinutes(minutes);
        r.setCompletedTime(time);
        return r;
    }

    @Test
    void ablationProtocolFullBeatsPopularBaseline() {
        // 基准时间：30 天前的 8 点，逐条递增 1 小时，保证每用户记录按时间有序
        LocalDateTime base = LocalDateTime.now().minusDays(30).withHour(8);
        LocalDateTime t = base;
        List<TaskRecord> records = new ArrayList<>();
        long uid = 1;

        // 三组噪音用户，每组 10 人 × 10 条记录（8 训练 + 2 测试）
        for (String cat : List.of("运动", "冥想", "阅读")) {
            for (int u = 0; u < 10; u++) {
                for (int i = 0; i < 10; i++) {
                    records.add(rec(uid, cat, 30, t));
                    t = t.plusHours(1);
                }
                uid++;
            }
        }
        // 学习用户 5 人 × 10 条（训练期与测试期都只做学习，总量远低于三个热门类别）
        for (int u = 0; u < 5; u++) {
            for (int i = 0; i < 10; i++) {
                records.add(rec(uid, "学习", 30, t));
                t = t.plusHours(1);
            }
            uid++;
        }
        when(taskRecordRepository.findAll()).thenReturn(records);

        Map<String, Map<String, Double>> results = evalService.evaluate();

        // 结构断言：5 档消融模型按固定顺序输出，指标齐全
        assertEquals(List.of("POPULAR", "CF", "CF+DECAY", "CF+TAG", "FULL"),
                new ArrayList<>(results.keySet()));
        for (Map<String, Double> m : results.values()) {
            assertTrue(m.containsKey("P@3") && m.containsKey("R@3")
                    && m.containsKey("P@5") && m.containsKey("R@5"));
        }
        assertEquals(35.0, results.get("FULL").get("评估用户数"), "35 个用户均应参与评估");

        // 效果断言：完整混合模型严格优于全局热度基线（论文核心实验结论的最小验证）
        assertTrue(results.get("FULL").get("P@3") > results.get("POPULAR").get("P@3"),
                String.format("FULL P@3=%s 应大于 POPULAR P@3=%s",
                        results.get("FULL").get("P@3"), results.get("POPULAR").get("P@3")));
        assertTrue(results.get("FULL").get("R@3") > results.get("POPULAR").get("R@3"));
    }

    @Test
    void emptyDataYieldsZeroMetrics() {
        when(taskRecordRepository.findAll()).thenReturn(List.of());
        Map<String, Map<String, Double>> results = evalService.evaluate();
        assertEquals(5, results.size());
        assertEquals(0.0, results.get("FULL").get("P@3"));
        assertEquals(0.0, results.get("FULL").get("评估用户数"));
    }
}
