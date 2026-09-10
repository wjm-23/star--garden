package com.stargarden.controller;

import com.stargarden.entity.TaskRecord;
import com.stargarden.repository.TaskRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 专注数据报告接口（数据可视化后端）：
 *   GET /api/report/trend?days=30   近 N 天每日专注时长趋势
 *   GET /api/report/category        类别分布（次数与时长）
 *   GET /api/report/heatmap         近 90 天热力日历（ECharts calendar 格式）
 *   GET /api/report/best-hours      24 小时专注分布（黄金专注时段）
 *   GET /api/report/annual          年度专注报告大屏聚合数据
 */
@RestController
@RequestMapping("/api/report")
public class ReportApiController {

    @Autowired
    private TaskRecordRepository taskRecordRepository;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/trend")
    public Map<String, Object> trend(@RequestParam(defaultValue = "30") int days,
                                     HttpServletRequest request) {
        Long userId = userId(request);
        Map<String, Object> res = new HashMap<>();
        if (userId == null) {
            res.put("success", false);
            res.put("msg", "未登录");
            return res;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(Math.min(Math.max(days, 1), 180) - 1L);
        List<TaskRecord> records = taskRecordRepository.findByUserIdAndCompletedTimeBetween(
                userId, start.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<LocalDate, Integer> byDay = records.stream()
                .filter(r -> r.getCompletedTime() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCompletedTime().toLocalDate(),
                        Collectors.summingInt(r -> r.getDurationMinutes() == null ? 0 : r.getDurationMinutes())));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.format(D));
            row.put("minutes", byDay.getOrDefault(d, 0));
            trend.add(row);
        }
        res.put("success", true);
        res.put("days", days);
        res.put("trend", trend);
        return res;
    }

    @GetMapping("/category")
    public Map<String, Object> category(HttpServletRequest request) {
        Long userId = userId(request);
        Map<String, Object> res = new HashMap<>();
        if (userId == null) {
            res.put("success", false);
            res.put("msg", "未登录");
            return res;
        }
        List<TaskRecord> records = taskRecordRepository.findByUserId(userId);

        Map<String, int[]> agg = new LinkedHashMap<>(); // [0]=次数 [1]=分钟
        for (TaskRecord r : records) {
            String category = r.getTaskCategory() != null
                    ? r.getTaskCategory()
                    : com.stargarden.service.TaskCategoryUtil.derive(r.getTaskName());
            int[] arr = agg.computeIfAbsent(category, k -> new int[2]);
            arr[0] += 1;
            arr[1] += r.getDurationMinutes() == null ? 0 : r.getDurationMinutes();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        agg.forEach((category, arr) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", category);
            row.put("count", arr[0]);
            row.put("minutes", arr[1]);
            list.add(row);
        });
        list.sort((a, b) -> Integer.compare((int) b.get("minutes"), (int) a.get("minutes")));
        res.put("success", true);
        res.put("items", list);
        return res;
    }

    @GetMapping("/heatmap")
    public Map<String, Object> heatmap(HttpServletRequest request) {
        Long userId = userId(request);
        Map<String, Object> res = new HashMap<>();
        if (userId == null) {
            res.put("success", false);
            res.put("msg", "未登录");
            return res;
        }
        LocalDate today = LocalDate.now();
        List<TaskRecord> records = taskRecordRepository.findByUserIdAndCompletedTimeBetween(
                userId, today.minusDays(89).atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<LocalDate, Integer> byDay = records.stream()
                .filter(r -> r.getCompletedTime() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCompletedTime().toLocalDate(),
                        Collectors.summingInt(r -> r.getDurationMinutes() == null ? 0 : r.getDurationMinutes())));

        List<List<Object>> data = new ArrayList<>();
        for (LocalDate d = today.minusDays(89); !d.isAfter(today); d = d.plusDays(1)) {
            data.add(Arrays.asList(d.format(D), byDay.getOrDefault(d, 0)));
        }
        res.put("success", true);
        res.put("data", data);
        return res;
    }

    @GetMapping("/best-hours")
    public Map<String, Object> bestHours(HttpServletRequest request) {
        Long userId = userId(request);
        Map<String, Object> res = new HashMap<>();
        if (userId == null) {
            res.put("success", false);
            res.put("msg", "未登录");
            return res;
        }
        List<TaskRecord> records = taskRecordRepository.findByUserId(userId);
        int[] hours = new int[24];
        int[] counts = new int[24];
        for (TaskRecord r : records) {
            if (r.getCompletedTime() == null) continue;
            int h = r.getCompletedTime().getHour();
            hours[h] += r.getDurationMinutes() == null ? 0 : r.getDurationMinutes();
            counts[h] += 1;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hour", h);
            row.put("minutes", hours[h]);
            row.put("count", counts[h]);
            list.add(row);
        }
        res.put("success", true);
        res.put("items", list);
        return res;
    }

    /** 年度专注报告：总览指标 + 类别分布 + 月度趋势 + 最专注的一天 */
    @GetMapping("/annual")
    public Map<String, Object> annual(HttpServletRequest request) {
        Long userId = userId(request);
        Map<String, Object> res = new HashMap<>();
        if (userId == null) {
            res.put("success", false);
            res.put("msg", "未登录");
            return res;
        }
        List<TaskRecord> records = taskRecordRepository.findByUserId(userId);
        int year = LocalDate.now().getYear();

        List<TaskRecord> yearRecords = records.stream()
                .filter(r -> r.getCompletedTime() != null)
                .filter(r -> r.getCompletedTime().getYear() == year)
                .collect(Collectors.toList());

        long totalMinutes = yearRecords.stream()
                .mapToLong(r -> r.getDurationMinutes() == null ? 0 : r.getDurationMinutes()).sum();
        long totalCount = yearRecords.size();
        long activeDays = yearRecords.stream()
                .map(r -> r.getCompletedTime().toLocalDate()).distinct().count();

        // 最专注的一天
        Map<LocalDate, Integer> byDay = new HashMap<>();
        yearRecords.forEach(r -> byDay.merge(r.getCompletedTime().toLocalDate(),
                r.getDurationMinutes() == null ? 0 : r.getDurationMinutes(), Integer::sum));
        Map.Entry<LocalDate, Integer> bestDay = byDay.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);

        // 月度趋势
        int[] monthMinutes = new int[12];
        yearRecords.forEach(r -> monthMinutes[r.getCompletedTime().getMonthValue() - 1] +=
                r.getDurationMinutes() == null ? 0 : r.getDurationMinutes());
        List<Map<String, Object>> months = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m + 1);
            row.put("minutes", monthMinutes[m]);
            months.add(row);
        }

        // 类别分布
        Map<String, Integer> catMinutes = new HashMap<>();
        yearRecords.forEach(r -> catMinutes.merge(
                r.getTaskCategory() != null ? r.getTaskCategory()
                        : com.stargarden.service.TaskCategoryUtil.derive(r.getTaskName()),
                r.getDurationMinutes() == null ? 0 : r.getDurationMinutes(), Integer::sum));
        List<Map<String, Object>> categories = catMinutes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", e.getKey());
                    row.put("minutes", e.getValue());
                    return row;
                }).collect(Collectors.toList());

        String favorite = categories.isEmpty() ? "暂无" : String.valueOf(categories.get(0).get("category"));

        res.put("success", true);
        res.put("year", year);
        res.put("totalMinutes", totalMinutes);
        res.put("totalHours", Math.round(totalMinutes / 6.0) / 10.0);
        res.put("totalCount", totalCount);
        res.put("activeDays", activeDays);
        res.put("favoriteCategory", favorite);
        res.put("bestDay", bestDay == null ? null : Map.of(
                "date", bestDay.getKey().format(D), "minutes", bestDay.getValue()));
        res.put("months", months);
        res.put("categories", categories);
        return res;
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }
}
