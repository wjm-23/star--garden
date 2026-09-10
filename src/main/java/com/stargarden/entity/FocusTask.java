package com.stargarden.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "focus_task")
public class FocusTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String taskName;       // 任务名称（如：学习、阅读）
    private String description;   // 任务描述
    private Integer defaultMinutes = 25; // 默认专注时长（分钟）
    private Long plantId;         // 关联植物 ID（来自 plant 表）
    private String plantName;     // 冗余存储植物名称，便于展示
    private String icon;          // emoji 图标
    private Integer sortOrder = 0;// 排序
    private boolean enabled = true; // 是否启用
}
