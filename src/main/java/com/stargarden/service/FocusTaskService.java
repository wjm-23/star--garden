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
        // 同步冗余的植物名称，便于前端展示
        if (task.getPlantId() != null) {
            plantRepository.findById(task.getPlantId()).ifPresent(p -> task.setPlantName(p.getName()));
        }
        return focusTaskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        focusTaskRepository.deleteById(id);
    }

    /** 首次启动：若 focus_task 表为空，把枚举里的默认任务播种进数据库 */
    @PostConstruct
    public void initSeed() {
        if (focusTaskRepository.count() == 0) {
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
        }
    }

    private String defaultDesc(String name) {
        switch (name) {
            case "学习": return "静下心来，专注学习一会儿";
            case "阅读": return "翻开一本书，享受阅读时光";
            case "运动": return "动起来，给身体充充电";
            case "早起": return "迎着晨光，开启元气满满的一天";
            case "冥想": return "放空自己，感受当下的呼吸";
            case "自定义": return "自定义时长，做你想做的事";
            default: return "专注做一件有意义的事";
        }
    }

    private String taskIcon(String name) {
        switch (name) {
            case "学习": return "📚";
            case "阅读": return "📖";
            case "运动": return "🏃";
            case "早起": return "🌅";
            case "冥想": return "🧘";
            default: return "✨";
        }
    }
}
