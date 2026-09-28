package com.stargarden.dto;

import lombok.Data;

/**
 * 任务修改请求体（PATCH 语义）。
 * 不直接复用 FocusTask 实体接收：实体字段自带默认值（defaultMinutes=25、enabled=true），
 * Jackson 反序列化时会把"未提交的字段"填成实体默认值，导致部分更新被误覆盖。
 * 本 DTO 全字段可空，null = 不修改。
 */
@Data
public class TaskUpdateRequest {
    private String taskName;
    private String category;
    private String description;
    private Integer defaultMinutes;
    private Long plantId;
    private String icon;
    private Integer sortOrder;
    private Boolean enabled;
}
