package com.stargarden.service;

import com.stargarden.entity.FocusTask;
import com.stargarden.enums.PlantTaskType;
import com.stargarden.repository.FocusTaskRepository;
import com.stargarden.repository.PlantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FocusTaskService {

    private final FocusTaskRepository focusTaskRepository;
    private final PlantRepository plantRepository;

    /** 管理页：全部任务（含禁用） */
    public List<FocusTask> listAll() {
        return focusTaskRepository.findAllByOrderBySortOrderAsc();
    }

    /** 用户任务大厅：仅启用任务 */
    public List<FocusTask> listEnabled() {
        return focusTaskRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    public FocusTask getById(Long id) {
        return focusTaskRepository.findById(id).orElse(null);
    }

    public FocusTask findByTaskName(String taskName) {
        return focusTaskRepository.findByTaskName(taskName);
    }

    @Transactional
    public FocusTask save(FocusTask task) {
        if (task.getPlantId() != null) {
            plantRepository.findById(task.getPlantId()).ifPresent(p -> task.setPlantName(p.getName()));
        }
        return focusTaskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        focusTaskRepository.deleteById(id);
    }

    /**
     * 启动时仅在 focus_task 表为空的情况下初始化内置任务（PlantTaskType 枚举 18 种）。
     * 任务已改为管理员可维护的数据（/api/admin/tasks），管理员的新增、删除、禁用
     * 不能被启动逻辑覆盖回枚举默认值，因此不再做"数量不一致即清空重插"。
     */
    @PostConstruct
    public void initSeed() {
        if (!focusTaskRepository.findAll().isEmpty()) return;
        int order = 0;
        for (PlantTaskType t : PlantTaskType.listAll()) {
            FocusTask ft = new FocusTask();
            ft.setTaskName(t.getTaskName());
            ft.setDescription(defaultDesc(t.getTaskName()));
            ft.setDefaultMinutes(t.getDefaultMinutes());
            ft.setPlantId(t.getPlantId());
            ft.setPlantName(t.getPlantName());
            ft.setIcon(taskIcon(t.getTaskName()));
            ft.setSortOrder(order++);
            ft.setEnabled(true);
            focusTaskRepository.save(ft);
        }
        System.out.println("[FocusTaskService] 已初始化专注任务 " + order + " 种");
    }

    private String defaultDesc(String name) {
        switch (name) {
            case "深度学习":  return "静下心来，啃一本专业书或刷一套题";
            case "编程开发":  return "敲代码的时间到了，把 idea 变成 reality";
            case "记忆训练":  return "背单词 / 背公式 / 速记训练";
            case "阅读思考":  return "翻开一本书，享受阅读与思考的时光";
            case "写作创作":  return "写日记、写代码注释、写一段文字";
            case "音乐聆听":  return "戴上耳机，让旋律陪伴专注";
            case "有氧运动":  return "跑步、骑行、快走，给身体充充电";
            case "自律打卡":  return "坚持一件小事，让自律成为习惯";
            case "睡眠冥想":  return "睡前放松，让大脑安静下来";
            case "早起打卡":  return "迎着晨光，开启元气满满的一天";
            case "规划复盘":  return "做计划、写复盘、整理待办";
            case "社交联结":  return "与朋友好好聊一次天";
            case "正念冥想":  return "放空自己，感受当下的呼吸";
            case "品茶静心":  return "泡一壶好茶，慢下来";
            case "烹饪专注":  return "认真做一顿饭，也是一种专注";
            case "创意构思":  return "头脑风暴，让灵感飞一会儿";
            case "整理收纳":  return "整理书桌、整理房间、整理心情";
            default:          return "自定义时长，做你想做的事";
        }
    }

    private String taskIcon(String name) {
        switch (name) {
            case "深度学习":  return "📚";
            case "编程开发":  return "💻";
            case "记忆训练":  return "🧠";
            case "阅读思考":  return "📖";
            case "写作创作":  return "✍️";
            case "音乐聆听":  return "🎵";
            case "有氧运动":  return "🏃";
            case "自律打卡":  return "✅";
            case "睡眠冥想":  return "😴";
            case "早起打卡":  return "🌅";
            case "规划复盘":  return "📋";
            case "社交联结":  return "💬";
            case "正念冥想":  return "🧘";
            case "品茶静心":  return "🍵";
            case "烹饪专注":  return "🍳";
            case "创意构思":  return "💡";
            case "整理收纳":  return "🧹";
            default:          return "✨";
        }
    }
}
