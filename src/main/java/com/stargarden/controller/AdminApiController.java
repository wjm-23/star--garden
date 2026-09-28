package com.stargarden.controller;

import com.stargarden.dto.TaskUpdateRequest;
import com.stargarden.entity.FocusTask;
import com.stargarden.entity.Plant;
import com.stargarden.entity.User;
import com.stargarden.repository.AchievementRepository;
import com.stargarden.repository.FriendLikeRepository;
import com.stargarden.repository.PlantRepository;
import com.stargarden.repository.TaskRecordRepository;
import com.stargarden.repository.UserGardenRepository;
import com.stargarden.repository.UserRepository;
import com.stargarden.service.EvalService;
import com.stargarden.service.FocusTaskService;
import com.stargarden.service.TaskCategoryUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员后台 API（/api/admin/**），开题报告第 15 个功能模块"系统管理"的服务端实现。
 * 每个接口入口统一做 ADMIN 角色校验（非管理员返回 403），覆盖三块能力：
 *   1. 推荐算法消融评估 —— 论文实验章节的数据出口（Precision@N / Recall@N，5 档消融）
 *   2. 用户管理 —— 查询 / 启用停用 / 角色调整 / 删除（级联清理行为数据）
 *   3. 任务大厅管理 —— 任务（focus_task）的增删改查，供任务大厅与推荐落地使用
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final UserRepository userRepository;
    private final PlantRepository plantRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final UserGardenRepository userGardenRepository;
    private final AchievementRepository achievementRepository;
    private final FriendLikeRepository friendLikeRepository;
    private final FocusTaskService focusTaskService;
    private final EvalService evalService;

    /** 允许的角色取值 */
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    /**
     * 校验当前登录用户为管理员；否则写出 403 响应并返回 null。
     * 身份来源：JwtInterceptor 解析出的 currentUserId（Session / JWT 双通道）。
     */
    private User requireAdmin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Long uid = (Long) request.getAttribute("currentUserId");
        User user = uid == null ? null : userRepository.findById(uid).orElse(null);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"msg\":\"需要管理员权限\"}");
            return null;
        }
        return user;
    }

    // ==================== 1. 消融评估 ====================

    /**
     * 运行推荐算法消融评估（论文实验）。同步执行，播种数据规模下秒级完成。
     * 返回：results（模型 -> 指标 -> 值）、elapsedMs、evaluatedAt。
     */
    @PostMapping("/eval/run")
    public Map<String, Object> runEval(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        long begin = System.currentTimeMillis();
        LinkedHashMap<String, Map<String, Double>> results = evalService.evaluate();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("results", results);
        res.put("elapsedMs", System.currentTimeMillis() - begin);
        res.put("evaluatedAt", LocalDateTime.now().withNano(0));
        return res;
    }

    // ==================== 2. 用户管理 ====================

    /** 用户列表（可按用户名/昵称模糊过滤），按注册时间倒序 */
    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(required = false) String keyword,
                                     HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        List<User> users = userRepository.findAll();
        users.sort(Comparator.comparing(User::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase();
        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : users) {
            if (kw != null
                    && (u.getUsername() == null || !u.getUsername().toLowerCase().contains(kw))
                    && (u.getNickname() == null || !u.getNickname().toLowerCase().contains(kw))) {
                continue;
            }
            items.add(userSummary(u));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("total", items.size());
        res.put("items", items);
        return res;
    }

    /** 启用 / 停用账号（停用后该用户无法登录） */
    @PutMapping("/users/{id}/enabled")
    public Map<String, Object> toggleEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                             HttpServletRequest request, HttpServletResponse response) throws Exception {
        User admin = requireAdmin(request, response);
        if (admin == null) return null;
        Map<String, Object> res = new HashMap<>();
        User u = userRepository.findById(id).orElse(null);
        if (u == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }
        if (u.getId().equals(admin.getId())) { res.put("success", false); res.put("msg", "不能停用自己的账号"); return res; }
        Boolean enabled = body.get("enabled");
        u.setEnabled(enabled == null ? !u.isEnabled() : enabled);
        userRepository.save(u);
        res.put("success", true);
        res.put("user", userSummary(u));
        return res;
    }

    /** 调整角色（USER / ADMIN），不允许修改自己的角色 */
    @PutMapping("/users/{id}/role")
    public Map<String, Object> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body,
                                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        User admin = requireAdmin(request, response);
        if (admin == null) return null;
        Map<String, Object> res = new HashMap<>();
        User u = userRepository.findById(id).orElse(null);
        if (u == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }
        if (u.getId().equals(admin.getId())) { res.put("success", false); res.put("msg", "不能修改自己的角色"); return res; }
        String role = body.get("role");
        if (!ROLES.contains(role)) { res.put("success", false); res.put("msg", "角色取值非法"); return res; }
        u.setRole(role);
        userRepository.save(u);
        res.put("success", true);
        res.put("user", userSummary(u));
        return res;
    }

    /** 删除用户及其全部关联数据（专注记录、花园、成就、好友点赞） */
    @DeleteMapping("/users/{id}")
    @Transactional
    public Map<String, Object> deleteUser(@PathVariable Long id,
                                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        User admin = requireAdmin(request, response);
        if (admin == null) return null;
        Map<String, Object> res = new HashMap<>();
        User u = userRepository.findById(id).orElse(null);
        if (u == null) { res.put("success", false); res.put("msg", "用户不存在"); return res; }
        if (u.getId().equals(admin.getId())) { res.put("success", false); res.put("msg", "不能删除自己的账号"); return res; }
        taskRecordRepository.deleteByUserId(u.getId());
        userGardenRepository.deleteByUserId(u.getId());
        achievementRepository.deleteByUserId(u.getId());
        friendLikeRepository.deleteByFromUserId(u.getId());
        friendLikeRepository.deleteByToUserId(u.getId());
        userRepository.delete(u);
        res.put("success", true);
        return res;
    }

    /** 用户对外输出（绝不含密码字段） */
    private Map<String, Object> userSummary(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("enabled", u.isEnabled());
        m.put("consecutiveDays", u.getConsecutiveDays());
        m.put("totalPlants", u.getTotalPlants());
        m.put("createTime", u.getCreateTime());
        return m;
    }

    // ==================== 3. 任务大厅管理 ====================

    /** 全部任务（含已禁用），带推导类别便于展示 */
    @GetMapping("/tasks")
    public Map<String, Object> tasks(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        List<Map<String, Object>> items = new ArrayList<>();
        for (FocusTask t : focusTaskService.listAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskName", t.getTaskName());
            m.put("taskCategory", TaskCategoryUtil.resolve(t.getCategory(), t.getTaskName()));
            m.put("description", t.getDescription());
            m.put("defaultMinutes", t.getDefaultMinutes());
            m.put("plantId", t.getPlantId());
            m.put("plantName", t.getPlantName());
            m.put("icon", t.getIcon());
            m.put("sortOrder", t.getSortOrder());
            m.put("enabled", t.isEnabled());
            items.add(m);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("items", items);
        return res;
    }

    /** 植物下拉选项（任务关联植物用） */
    @GetMapping("/plants")
    public Map<String, Object> plants(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Plant p : plantRepository.findAll()) {
            items.add(Map.of("id", p.getId(), "name", p.getName(),
                    "icon", p.getIcon() == null ? "🌱" : p.getIcon()));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("items", items);
        return res;
    }

    /** 新增任务 */
    @PostMapping("/tasks")
    public Map<String, Object> createTask(@RequestBody FocusTask body,
                                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        Map<String, Object> res = new HashMap<>();
        String error = validateTask(null, body);
        if (error != null) { res.put("success", false); res.put("msg", error); return res; }
        body.setId(null);
        FocusTask saved = focusTaskService.save(normalize(body));
        res.put("success", true);
        res.put("task", saved);
        return res;
    }

    /** 修改任务（支持部分字段提交，如仅切换启用状态） */
    @PutMapping("/tasks/{id}")
    public Map<String, Object> updateTask(@PathVariable Long id, @RequestBody TaskUpdateRequest body,
                                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        Map<String, Object> res = new HashMap<>();
        FocusTask exist = focusTaskService.getById(id);
        if (exist == null) { res.put("success", false); res.put("msg", "任务不存在"); return res; }
        // 先合并出最终状态再校验：部分字段提交（如仅传 enabled）时 name/minutes 取原值参与校验
        merge(exist, body);
        String error = validateTask(id, exist);
        if (error != null) { res.put("success", false); res.put("msg", error); return res; }
        FocusTask saved = focusTaskService.save(exist);
        res.put("success", true);
        res.put("task", saved);
        return res;
    }

    /** 删除任务 */
    @DeleteMapping("/tasks/{id}")
    public Map<String, Object> deleteTask(@PathVariable Long id,
                                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requireAdmin(request, response) == null) return null;
        Map<String, Object> res = new HashMap<>();
        FocusTask exist = focusTaskService.getById(id);
        if (exist == null) { res.put("success", false); res.put("msg", "任务不存在"); return res; }
        focusTaskService.delete(id);
        res.put("success", true);
        return res;
    }

    /** 任务字段校验：名称必填且不与其他任务重名，时长 1-240 分钟 */
    private String validateTask(Long selfId, FocusTask t) {
        if (t.getTaskName() == null || t.getTaskName().trim().isEmpty()) return "任务名称不能为空";
        String name = t.getTaskName().trim();
        FocusTask dup = focusTaskService.findByTaskName(name);
        if (dup != null && !dup.getId().equals(selfId)) return "任务名称已存在";
        if (t.getDefaultMinutes() != null && (t.getDefaultMinutes() < 1 || t.getDefaultMinutes() > 240)) {
            return "默认时长需在 1-240 分钟之间";
        }
        // 分类若显式提交，必须是合法分类之一（分类决定任务大厅标签与推荐归类）
        if (t.getCategory() != null && !t.getCategory().isBlank()
                && !TaskCategoryUtil.isValid(t.getCategory())) {
            return "任务分类不合法，可选：" + String.join("/", TaskCategoryUtil.CATEGORIES);
        }
        return null;
    }

    /** 新增时补默认值 */
    private FocusTask normalize(FocusTask t) {
        if (t.getDefaultMinutes() == null) t.setDefaultMinutes(25);
        if (t.getSortOrder() == null) t.setSortOrder(0);
        if (t.getIcon() == null || t.getIcon().isBlank()) t.setIcon("✨");
        if (t.getDescription() == null) t.setDescription("");
        // 未指定分类时按任务名推导一个，保证每条任务都有分类
        t.setCategory(TaskCategoryUtil.resolve(t.getCategory(), t.getTaskName()));
        t.setTaskName(t.getTaskName().trim());
        return t;
    }

    /** 修改时仅覆盖提交的字段（null = 不修改），避免实体默认值污染部分更新 */
    private FocusTask merge(FocusTask exist, TaskUpdateRequest body) {
        if (body.getTaskName() != null) exist.setTaskName(body.getTaskName().trim());
        if (body.getCategory() != null) exist.setCategory(body.getCategory().trim());
        if (body.getDescription() != null) exist.setDescription(body.getDescription());
        if (body.getDefaultMinutes() != null) exist.setDefaultMinutes(body.getDefaultMinutes());
        if (body.getPlantId() != null) exist.setPlantId(body.getPlantId());
        if (body.getIcon() != null && !body.getIcon().isBlank()) exist.setIcon(body.getIcon());
        if (body.getSortOrder() != null) exist.setSortOrder(body.getSortOrder());
        if (body.getEnabled() != null) exist.setEnabled(body.getEnabled());
        // 历史数据或未提交分类时兜底补一个，保证分类字段始终有值
        if (exist.getCategory() == null || exist.getCategory().isBlank()) {
            exist.setCategory(TaskCategoryUtil.resolve(null, exist.getTaskName()));
        }
        return exist;
    }
}
