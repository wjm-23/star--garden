package com.stargarden.enums;

import java.util.Arrays;
import java.util.List;

public enum PlantTaskType {
    STUDY(1L, "学习", 25, "智慧藤"),
    READ(2L, "阅读", 20, "专注花"),
    SPORT(3L, "运动", 30, "活力草"),
    EARLY_RISE(4L, "早起", 0, "晨光花"),
    MEDITATE(5L, "冥想", 10, "宁静叶"),
    CUSTOM(6L, "自定义", 25, "星芽草");

    private final Long plantId;
    private final String taskName;
    private final int defaultMinutes;
    private final String plantName;

    PlantTaskType(Long plantId, String taskName, int defaultMinutes, String plantName) {
        this.plantId = plantId;
        this.taskName = taskName;
        this.defaultMinutes = defaultMinutes;
        this.plantName = plantName;
    }

    public Long getPlantId() { return plantId; }
    public String getTaskName() { return taskName; }
    public int getDefaultMinutes() { return defaultMinutes; }
    public String getPlantName() { return plantName; }

    public static PlantTaskType matchByTaskName(String name) {
        if (name == null) return CUSTOM;
        return Arrays.stream(values())
                .filter(t -> name.contains(t.taskName))
                .findFirst()
                .orElse(CUSTOM);
    }

    public static PlantTaskType getByPlantId(Long plantId) {
        if (plantId == null) return CUSTOM;
        return Arrays.stream(values())
                .filter(t -> t.plantId.equals(plantId))
                .findFirst()
                .orElse(CUSTOM);
    }

    public static String getTaskNameByPlantId(Long plantId) {
        return getByPlantId(plantId).getTaskName();
    }

    public static int getDefaultMinutes(String name) {
        PlantTaskType type = matchByTaskName(name);
        return type.defaultMinutes;
    }

    public static List<PlantTaskType> listAll() {
        return Arrays.asList(values());
    }
}
