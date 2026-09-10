package com.stargarden.controller;

import com.stargarden.entity.FocusTask;
import com.stargarden.entity.Plant;
import com.stargarden.entity.User;
import com.stargarden.repository.PlantRepository;
import com.stargarden.repository.UserRepository;
import com.stargarden.service.EvalService;
import com.stargarden.service.FocusTaskService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final PlantRepository plantRepository;
    private final FocusTaskService focusTaskService;
    private final EvalService evalService;

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @GetMapping("/user/toggle")
    public String toggleUser(@RequestParam Long id) {
        userRepository.findById(id).ifPresent(u -> {
            u.setEnabled(!u.isEnabled());
            userRepository.save(u);
            log.info("管理员切换用户启用状态: userId={}, enabled={}", id, u.isEnabled());
        });
        return "redirect:/admin/users";
    }

    @GetMapping("/plants")
    public String plants(Model model) {
        model.addAttribute("plants", plantRepository.findAll());
        return "admin/plants";
    }

    @GetMapping("/plant/add")
    public String addPlantPage(Model model) {
        model.addAttribute("plant", new Plant());
        return "admin/plant_form";
    }

    @PostMapping("/plant/save")
    public String savePlant(Plant plant) {
        plantRepository.save(plant);
        log.info("管理员保存植物: plant={}", plant.getName());
        return "redirect:/admin/plants";
    }

    @GetMapping("/plant/edit")
    public String editPlant(@RequestParam Long id, Model model) {
        plantRepository.findById(id).ifPresent(p -> model.addAttribute("plant", p));
        return "admin/plant_form";
    }

    @GetMapping("/plant/delete")
    public String deletePlant(@RequestParam Long id) {
        plantRepository.deleteById(id);
        log.info("管理员删除植物: plantId={}", id);
        return "redirect:/admin/plants";
    }

    /** 专注任务管理（增删改查，仅管理员可见入口） */
    @GetMapping("/tasks")
    public String focusTasks(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        model.addAttribute("user", user);
        model.addAttribute("taskList", focusTaskService.listAll());
        model.addAttribute("plants", plantRepository.findAll());
        return "admin/tasks";
    }

    @PostMapping("/tasks/save")
    public String saveFocusTask(@ModelAttribute FocusTask task,
                                @RequestParam(value = "taskEnabled", required = false, defaultValue = "false") boolean taskEnabled) {
        task.setEnabled(taskEnabled);
        focusTaskService.save(task);
        log.info("管理员保存专注任务: {}", task.getTaskName());
        return "redirect:/admin/tasks";
    }

    @GetMapping("/tasks/delete/{id}")
    public String deleteFocusTask(@PathVariable Long id) {
        focusTaskService.delete(id);
        log.info("管理员删除专注任务: id={}", id);
        return "redirect:/admin/tasks";
    }

    /** 推荐算法离线评估（消融实验），验证混合推荐相较基础协同过滤的提升 */
    @GetMapping("/eval")
    public String eval(Model model) {
        long start = System.currentTimeMillis();
        LinkedHashMap<String, Map<String, Double>> results = evalService.evaluate();
        long costMs = System.currentTimeMillis() - start;

        model.addAttribute("results", results);
        model.addAttribute("costMs", costMs);
        Map<String, String> modelDesc = new LinkedHashMap<>();
        modelDesc.put("POPULAR", "全局热门基线：按全站任务完成频次推荐（非个性化）");
        modelDesc.put("CF", "UserCF 协同过滤：用户-类别余弦相似度");
        modelDesc.put("CF+DECAY", "CF + 时间衰减：近期行为权重更高（指数衰减）");
        modelDesc.put("CF+TAG", "CF+DECAY + 标签相似度因子");
        modelDesc.put("FULL", "完整混合模型：0.5CF + 0.3标签 + 0.2时段 + 冷启动策略");
        model.addAttribute("modelDesc", modelDesc);
        return "admin/eval";
    }
}
