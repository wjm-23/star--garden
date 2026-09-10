package com.stargarden.controller;

import com.stargarden.entity.*;
import com.stargarden.repository.*;
import com.stargarden.service.GardenService;
import com.stargarden.service.RecommendationService;
import com.stargarden.service.UserService;
import com.stargarden.service.FocusTaskService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GardenController {

    private final GardenService gardenService;
    private final PlantRepository plantRepository;
    private final UserGardenRepository userGardenRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final AchievementRepository achievementRepository;
    private final FriendLikeRepository friendLikeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final RecommendationService recommendationService;
    private final FocusTaskService focusTaskService;

    @GetMapping("/tasks")
    public String tasksPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        // 混合推荐（UserCF+时间衰减+标签+时段），结果为任务大厅真实可选任务
        model.addAttribute("recommendations", recommendationService.recommend(user.getId(), 3));
        // 管理员在 /admin/tasks 维护的专注任务（仅启用项）动态展示在任务大厅
        model.addAttribute("focusTasks", focusTaskService.listEnabled());
        return "tasks";
    }

    @PostMapping("/task/start")
    @ResponseBody
    public Map<String, Object> startTask(@RequestParam String taskName, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("msg", "请先登录");
            return result;
        }
        // 将本次专注会话绑定到「开始者」账号，防止切换账号后任务记到别人头上
        session.setAttribute("focusActiveUserId", user.getId());
        session.setAttribute("focusTaskName", taskName);
        result.put("success", true);
        return result;
    }

    @PostMapping("/task/cancel")
    @ResponseBody
    public Map<String, Object> cancelTask(HttpSession session) {
        // 主动放弃专注时清除会话绑定
        session.removeAttribute("focusActiveUserId");
        session.removeAttribute("focusTaskName");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @PostMapping("/task/complete")
    @ResponseBody
    public Map<String, Object> completeTask(@RequestParam String taskName,
                                            @RequestParam int durationMinutes,
                                            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("msg", "请先登录");
            return result;
        }
        // 关键校验：只有「开始专注时登录的账号」才能完成并领取奖励，
        // 否则会出现 A 开始、切到 B 登录后任务记到 B 头上的串号问题。
        Long activeUid = (Long) session.getAttribute("focusActiveUserId");
        if (activeUid == null || !activeUid.equals(user.getId())) {
            result.put("success", false);
            result.put("msg", "本次专注不属于当前账号，无法提交（请用开始专注时的账号完成）");
            return result;
        }
        try {
            Plant reward = gardenService.completeTaskAndGetPlant(user, taskName, durationMinutes);
            // 更新 session 中 user 的统计值
            session.setAttribute("user", userService.findById(user.getId()).orElse(user));
            // 完成后清除会话绑定，防止一次 start 被重复使用
            session.removeAttribute("focusActiveUserId");
            session.removeAttribute("focusTaskName");
            result.put("success", true);
            result.put("plantId", reward.getId());
            result.put("plantName", reward.getName());
            result.put("plantImage", reward.getImageUrl());
            result.put("plantRarity", reward.getRarity());
        } catch (Exception e) {
            log.error("完成任务失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("msg", "任务提交失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/garden")
    public String gardenPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        Map<String, Object> gardenMap = gardenService.getUserGardenMap(user);
        if (gardenMap == null) gardenMap = new HashMap<>();

        model.addAttribute("gardenMap", gardenMap);
        model.addAttribute("pendingPlants", gardenService.getPendingPlants(user));
        model.addAttribute("gardenSize", user.getGardenSize() == null ? 6 : user.getGardenSize());
        model.addAttribute("user", user);
        log.debug("用户 {} 进入花园页面，种植格数: {}", user.getUsername(), gardenMap.size());
        return "garden";
    }

    @PostMapping("/garden/plant")
    @ResponseBody
    public Map<String, Object> plantToGarden(@RequestParam Long plantId,
                                             @RequestParam int x,
                                             @RequestParam int y,
                                             HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("msg", "请先登录");
            return result;
        }
        try {
            gardenService.plantToGarden(user, plantId, x, y);
            result.put("success", true);
            result.put("msg", "种植成功！");
        } catch (Exception e) {
            log.warn("种植失败: user={}, x={}, y={}, err={}", user.getUsername(), x, y, e.getMessage());
            result.put("success", false);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @PostMapping("/garden/remove")
    @ResponseBody
    public Map<String, Object> removePlant(@RequestParam int x,
                                           @RequestParam int y,
                                           HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("msg", "请先登录");
            return result;
        }
        try {
            gardenService.removePlant(user, x, y);
            result.put("success", true);
            result.put("msg", "已铲除，格子已腾空");
        } catch (Exception e) {
            log.warn("铲除失败: user={}, x={}, y={}, err={}", user.getUsername(), x, y, e.getMessage());
            result.put("success", false);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @PostMapping("/garden/expand")
    @ResponseBody
    public Map<String, Object> expandGarden(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("msg", "请先登录");
            return result;
        }
        try {
            int newSize = gardenService.expandGarden(user);
            // 同步 session 中的 user，保证 gardenSize 即时生效
            session.setAttribute("user", userService.findById(user.getId()).orElse(user));
            result.put("success", true);
            result.put("gardenSize", newSize);
            result.put("msg", "花园已扩建至 " + newSize + "×" + newSize + "！");
        } catch (Exception e) {
            log.warn("扩建失败: user={}, err={}", user.getUsername(), e.getMessage());
            result.put("success", false);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    @GetMapping("/encyclopedia")
    public String encyclopediaPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        List<Plant> allPlants = plantRepository.findAll();
        List<UserGarden> userPlants = userGardenRepository.findByUserId(user.getId());
        Set<Long> unlockedIds = new HashSet<>();
        for (UserGarden ug : userPlants) {
            unlockedIds.add(ug.getPlantId());
        }
        model.addAttribute("allPlants", allPlants);
        model.addAttribute("unlockedIds", unlockedIds);
        int progressPercent = allPlants.isEmpty() ? 0
                : (int) Math.round((double) unlockedIds.size() * 100 / allPlants.size());
        model.addAttribute("progressPercent", progressPercent);
        return "encyclopedia";
    }

    @GetMapping("/history")
    public String historyPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        List<TaskRecord> records = gardenService.getUserRecentTasks(user);
        // 把实体列表也放入 model，供模板 th:if 判断与 #historyContainer 容器渲染；
        // 否则 th:if="${records != null}" 永远为假，容器不生成 → 历史列表空白。
        model.addAttribute("records", records);
        // 构建可 JSON 序列化的 DTO 列表：直接传实体会被 Thymeleaf JS 内联序列化成
        // Java 的 toString（如 TaskRecord(id=1,...)），导致前端脚本崩溃、历史列表空白。
        List<Map<String, Object>> recordDtos = records.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("taskName", r.getTaskName());
            m.put("completedTime", r.getCompletedTime() != null ? r.getCompletedTime().toString() : "");
            m.put("durationMinutes", r.getDurationMinutes());
            m.put("plantId", r.getPlantId());
            m.put("planted", r.isPlanted());
            return m;
        }).collect(Collectors.toList());

        // 把 plantId 对应植物名称也传过去
        Set<Long> plantIds = records.stream()
                .map(TaskRecord::getPlantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> plantNameMap = plantRepository.findAllById(plantIds).stream()
                .collect(Collectors.toMap(Plant::getId, Plant::getName));

        // 用 Jackson 序列化为 JSON 字符串，避免 Thymeleaf JS 内联把 List/Map 序列化成无效的 Java toString 格式
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            model.addAttribute("recordsJson", om.writeValueAsString(recordDtos));
            model.addAttribute("plantMapJson", om.writeValueAsString(plantNameMap));
        } catch (Exception e) {
            model.addAttribute("recordsJson", "[]");
            model.addAttribute("plantMapJson", "{}");
        }
        return "history";
    }

    @GetMapping("/achievements")
    public String achievementsPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        List<Achievement> achievements = gardenService.getUserAchievements(user);
        // 修复成就判断逻辑：按 achievementType 精确判断，而非用 size 粗略判断
        Set<String> achievedTypes = achievements.stream()
                .map(Achievement::getAchievementType)
                .collect(Collectors.toSet());
        model.addAttribute("achievements", achievements);
        model.addAttribute("achievedTypes", achievedTypes);
        model.addAttribute("user", user);
        return "achievements";
    }

    @GetMapping("/friends")
    public String friendsPage() {
        return "friends";
    }

    @GetMapping("/friends/search")
    @ResponseBody
    public Map<String, Object> searchFriend(@RequestParam String username, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            result.put("success", false);
            result.put("msg", "请先登录");
            return result;
        }

        User friend = userService.findByUsername(username).orElse(null);
        if (friend == null) {
            result.put("success", false);
            result.put("msg", "用户不存在");
            return result;
        }

        Map<String, Object> gardenMap = gardenService.getUserGardenMap(friend);
        log.debug("查询好友 {} 的花园，格数={}", username, gardenMap.size());

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        boolean hasLikedToday = friendLikeRepository.existsByFromUserIdAndToUserIdAndGardenViewTimeAfter(
                currentUser.getId(), friend.getId(), todayStart);
        int likeCount = friendLikeRepository.countByToUserId(friend.getId());

        result.put("success", true);
        result.put("id", friend.getId());
        result.put("username", friend.getUsername());
        result.put("nickname", friend.getNickname());
        result.put("totalPlants", friend.getTotalPlants());
        result.put("gardenMap", gardenMap);
        result.put("hasLikedToday", hasLikedToday);
        result.put("likeCount", likeCount);
        return result;
    }

    @PostMapping("/friends/like")
    @ResponseBody
    public Map<String, Object> likeFriend(@RequestParam Long friendId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            result.put("success", false);
            return result;
        }
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        if (friendLikeRepository.existsByFromUserIdAndToUserIdAndGardenViewTimeAfter(currentUser.getId(), friendId, todayStart)) {
            result.put("success", false);
            result.put("msg", "今天已经点过赞了");
            return result;
        }
        FriendLike like = new FriendLike();
        like.setFromUserId(currentUser.getId());
        like.setToUserId(friendId);
        like.setGardenViewTime(LocalDateTime.now());
        friendLikeRepository.save(like);
        log.debug("用户 {} 给好友 {} 点赞成功", currentUser.getUsername(), friendId);

        result.put("success", true);
        result.put("newCount", friendLikeRepository.countByToUserId(friendId));
        return result;
    }

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(User updatedUser, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        User user = userService.findById(sessionUser.getId()).orElse(null);
        if (user != null) {
            user.setNickname(updatedUser.getNickname());
            user.setAvatarUrl(updatedUser.getAvatarUrl());
            userService.update(user);
            session.setAttribute("user", user);
            log.info("用户 {} 更新了个人资料", user.getUsername());
        }
        return "redirect:/profile";
    }
}
