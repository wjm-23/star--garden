package com.stargarden.controller;

import com.stargarden.entity.Achievement;
import com.stargarden.entity.FocusTask;
import com.stargarden.entity.Plant;
import com.stargarden.entity.TaskRecord;
import com.stargarden.entity.User;
import com.stargarden.entity.UserGarden;
import com.stargarden.repository.AchievementRepository;
import com.stargarden.repository.FocusTaskRepository;
import com.stargarden.repository.PlantRepository;
import com.stargarden.repository.TaskRecordRepository;
import com.stargarden.repository.UserGardenRepository;
import com.stargarden.service.FocusTaskService;
import com.stargarden.service.GardenService;
import com.stargarden.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前端 Vue3 SPA 所需的 JSON API（JwtInterceptor 双通道认证）。
 * 聚合了 任务大厅 / 用户资料 / 专注计时 / 花园 / 成就 / 图鉴 六组接口，
 * 供前后端分离版消费；原 Thymeleaf Controller 保留不动。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FocusApiController {

    private final UserService userService;
    private final FocusTaskService focusTaskService;
    private final GardenService gardenService;
    private final FocusTaskRepository focusTaskRepository;
    private final PlantRepository plantRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final UserGardenRepository userGardenRepository;
    private final AchievementRepository achievementRepository;

    // ==================== 任务大厅 ====================

    @GetMapping("/tasks")
    public Map<String, Object> listTasks(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        List<FocusTask> list = focusTaskService.listEnabled();
        res.put("success", true);
        res.put("tasks", list.stream().map(this::taskDto).collect(Collectors.toList()));
        return res;
    }

    // ==================== 当前用户资料 ====================

    @GetMapping("/user/profile")
    public Map<String, Object> profile(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User u = userService.findById(uid).orElse(null);
        if (u == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", u.getId());
        profile.put("username", u.getUsername());
        profile.put("nickname", u.getNickname());
        profile.put("email", u.getEmail());
        profile.put("role", u.getRole());
        profile.put("gardenSize", u.getGardenSize() == null ? 6 : u.getGardenSize());
        profile.put("consecutiveDays", u.getConsecutiveDays() == null ? 0 : u.getConsecutiveDays());
        profile.put("totalPlants", u.getTotalPlants() == null ? 0 : u.getTotalPlants());
        res.put("success", true);
        res.put("user", profile);
        return res;
    }

    // ==================== 专注计时（JWT 版，替换原 Session 通道） ====================

    @PostMapping("/focus/start")
    public Map<String, Object> startFocus(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        String taskName = (String) body.getOrDefault("taskName", "");
        Integer minutes = body.get("minutes") == null ? 25 : ((Number) body.get("minutes")).intValue();
        if (taskName.isEmpty()) { res.put("success", false); res.put("msg", "任务名不能为空"); return res; }
        // 轻量校验：任务大厅是否存在该任务
        FocusTask ft = focusTaskService.findByTaskName(taskName);
        int actualMinutes = (ft != null && ft.getDefaultMinutes() != null) ? ft.getDefaultMinutes() : minutes;

        // 不持久化"开始会话"——Vue 前端自行计时，完成时提交服务端即可（服务端记录 completedTime 作为真相源）
        res.put("success", true);
        res.put("taskName", taskName);
        res.put("minutes", actualMinutes);
        res.put("serverNow", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return res;
    }

    @PostMapping("/focus/complete")
    public Map<String, Object> completeFocus(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        String taskName = (String) body.getOrDefault("taskName", "");
        Integer durationMinutes = body.get("durationMinutes") == null
                ? 25 : ((Number) body.get("durationMinutes")).intValue();
        if (taskName.isEmpty()) { res.put("success", false); res.put("msg", "任务名不能为空"); return res; }
        if (durationMinutes < 1 || durationMinutes > 480) {
            res.put("success", false);
            res.put("msg", "专注时长需在 1~480 分钟之间");
            return res;
        }
        User user = userService.findById(uid).orElse(null);
        if (user == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }
        try {
            com.stargarden.entity.Plant reward = gardenService.completeTaskAndGetPlant(user, taskName, durationMinutes);
            // 同步最新 user 到返回体（totalPlants / consecutiveDays）
            User reloaded = userService.findById(uid).orElse(user);

            Map<String, Object> plantInfo = new LinkedHashMap<>();
            plantInfo.put("id", reward.getId());
            plantInfo.put("name", reward.getName());
            plantInfo.put("rarity", reward.getRarity());
            plantInfo.put("imageUrl", reward.getImageUrl());

            res.put("success", true);
            res.put("plant", plantInfo);
            res.put("newTotalPlants", reloaded.getTotalPlants());
            res.put("newConsecutiveDays", reloaded.getConsecutiveDays());
        } catch (Exception e) {
            res.put("success", false);
            res.put("msg", "专注提交失败：" + e.getMessage());
        }
        return res;
    }

    // ==================== 花园 ====================

    @GetMapping("/garden/map")
    public Map<String, Object> gardenMap(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        Map<String, Object> garden = gardenService.getUserGardenMap(user);
        res.put("success", true);
        res.put("garden", garden);
        res.put("gardenSize", user.getGardenSize() == null ? 6 : user.getGardenSize());
        return res;
    }

    @GetMapping("/garden/pending")
    public Map<String, Object> gardenPending(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        res.put("success", true);
        res.put("pending", gardenService.getPendingPlants(user));
        return res;
    }

    @PostMapping("/garden/plant")
    public Map<String, Object> plant(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        Long plantId = ((Number) body.get("plantId")).longValue();
        int x = ((Number) body.get("x")).intValue();
        int y = ((Number) body.get("y")).intValue();
        try {
            UserGarden planted = gardenService.plantToGarden(user, plantId, x, y);
            int stage = gardenService.stageForPlant(uid, plantId, planted.getPlantTime());
            res.put("success", true);
            res.put("msg", "种植成功");
            res.put("stage", stage);
            res.put("stageName", GardenService.STAGE_NAMES[stage]);
        } catch (Exception e) {
            res.put("success", false);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @PostMapping("/garden/remove")
    public Map<String, Object> remove(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        int x = ((Number) body.get("x")).intValue();
        int y = ((Number) body.get("y")).intValue();
        try {
            gardenService.removePlant(user, x, y);
            res.put("success", true);
            res.put("msg", "已铲除");
        } catch (Exception e) {
            res.put("success", false);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @PostMapping("/garden/grow")
    public Map<String, Object> grow(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        int x = ((Number) body.get("x")).intValue();
        int y = ((Number) body.get("y")).intValue();
        Object pid = body.get("plantId");
        Long plantId = pid == null ? null : ((Number) pid).longValue();
        try {
            Map<String, Object> info = gardenService.growPlant(user, x, y, plantId);
            res.put("success", true);
            res.put("msg", "🌱 种植成功");
            res.putAll(info);
        } catch (Exception e) {
            res.put("success", false);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    @PostMapping("/garden/expand")
    public Map<String, Object> expand(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        try {
            int newSize = gardenService.expandGarden(user);
            User reloaded = userService.findById(uid).orElse(user);
            res.put("success", true);
            res.put("gardenSize", newSize);
            res.put("msg", "花园扩建至 " + newSize + "×" + newSize);
            res.put("user", Map.of("gardenSize", reloaded.getGardenSize()));
        } catch (Exception e) {
            res.put("success", false);
            res.put("msg", e.getMessage());
        }
        return res;
    }

    // ==================== 专注历史 ====================

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "30") int days, HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        LocalDateTime start = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 180));
        List<TaskRecord> records = taskRecordRepository.findByUserIdAndCompletedTimeBetween(uid, start, LocalDateTime.now());
        // 关联 plantId → 植物名
        Map<Long, Plant> plantMap = plantRepository.findAll().stream()
                .collect(Collectors.toMap(Plant::getId, p -> p, (a, b) -> a));
        List<Map<String, Object>> items = records.stream().sorted((a, b) -> {
            if (a.getCompletedTime() == null) return 1;
            if (b.getCompletedTime() == null) return -1;
            return b.getCompletedTime().compareTo(a.getCompletedTime());
        }).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("taskName", r.getTaskName());
            m.put("taskCategory", r.getTaskCategory());
            m.put("durationMinutes", r.getDurationMinutes());
            m.put("completedTime", r.getCompletedTime() == null ? null : r.getCompletedTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            Plant p = r.getPlantId() == null ? null : plantMap.get(r.getPlantId());
            m.put("plantName", p == null ? null : p.getName());
            m.put("planted", r.isPlanted());
            return m;
        }).collect(Collectors.toList());
        res.put("success", true);
        res.put("items", items);
        return res;
    }

    // ==================== 成就 ====================

    @GetMapping("/achievements")
    public Map<String, Object> achievements(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        // 以真实数据（累计植物数 / 连续打卡 / 点赞数）实时推导成就，并把应得的成就补齐落库，
        // 避免出现"模拟账号有 9 株植物却 0 成就"这类与数据对不上的情况。
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized(res);
        gardenService.syncAchievements(user);
        List<Achievement> list = achievementRepository.findByUserId(uid);
        res.put("success", true);
        res.put("achievements", list);
        return res;
    }

    // ==================== 植物图鉴 ====================

    @GetMapping("/encyclopedia")
    public Map<String, Object> encyclopedia(HttpServletRequest request) {
        Long uid = uid(request);
        Map<String, Object> res = new HashMap<>();
        if (uid == null) return unauthorized(res);
        List<Plant> all = plantRepository.findAll();
        Set<Long> unlocked = userGardenRepository.findByUserId(uid).stream()
                .map(UserGarden::getPlantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 同时计入"获得过但可能已铲除"（完成过的 task_record 里出现过的 plantId）
        Set<Long> earned = taskRecordRepository.findByUserId(uid).stream()
                .map(TaskRecord::getPlantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        unlocked = new HashSet<>(unlocked);
        unlocked.addAll(earned);
        res.put("success", true);
        res.put("plants", all);
        res.put("unlockedIds", unlocked);
        res.put("progressPercent", all.isEmpty() ? 0
                : (int) Math.round((double) unlocked.size() * 100 / all.size()));
        return res;
    }

    // ==================== 工具 ====================

    private Long uid(HttpServletRequest request) {
        return (Long) request.getAttribute("currentUserId");
    }

    private Map<String, Object> unauthorized(Map<String, Object> res) {
        res.put("success", false);
        res.put("msg", "未登录");
        return res;
    }

    private Map<String, Object> taskDto(FocusTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskName", t.getTaskName());
        // 分类由后端统一给出（任务自身配置优先），前端不再自行按任务名猜测
        m.put("category", com.stargarden.service.TaskCategoryUtil.resolve(t.getCategory(), t.getTaskName()));
        m.put("description", t.getDescription());
        m.put("defaultMinutes", t.getDefaultMinutes());
        m.put("plantId", t.getPlantId());
        m.put("plantName", t.getPlantName());
        m.put("icon", t.getIcon());
        m.put("sortOrder", t.getSortOrder());
        m.put("enabled", t.isEnabled());
        return m;
    }
}
