package com.stargarden.dto;

/**
 * 推荐结果条目：推荐的具体任务、所属类别、混合得分、建议时长、
 * 任务图标与可获得的植物（可解释性推荐）。
 */
public class RecommendationItem {

    private String taskName;
    private String category;
    private double score;
    private Integer minutes;
    private String reason;
    private String icon;
    private String plantName;
    private Long plantId;

    public RecommendationItem() { }

    public RecommendationItem(String taskName, String category, double score, String reason) {
        this.taskName = taskName;
        this.category = category;
        this.score = score;
        this.reason = reason;
    }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getPlantName() { return plantName; }
    public void setPlantName(String plantName) { this.plantName = plantName; }

    public Long getPlantId() { return plantId; }
    public void setPlantId(Long plantId) { this.plantId = plantId; }
}
