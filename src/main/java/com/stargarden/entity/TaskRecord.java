package com.stargarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "task_record")
public class TaskRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String taskName;

    /** 任务类别标签（学习/阅读/运动/早起/冥想/工作/生活），推荐算法与数据报告的核心维度 */
    private String taskCategory;

    private Integer durationMinutes;
    private LocalDateTime completedTime;
    private Long plantId;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean planted = false;
}
