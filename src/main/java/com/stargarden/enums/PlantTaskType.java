package com.stargarden.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 18 种专注任务类型，与植物图鉴 18 种一一对应。
 * 按类别分 6 组：学习 / 阅读 / 运动 / 早起 / 冥想 / 创造 × 各 3 种稀有度。
 */
public enum PlantTaskType {
    // ===== 学习类 =====
    STUDY(1L,  "深度学习", 25, "智慧藤"),
    CODE(9L,   "编程开发", 45, "逻辑松"),
    MEMORY(11L,"记忆训练", 15, "记忆蕨"),

    // ===== 阅读类 =====
    READ(2L,   "阅读思考", 20, "专注花"),
    WRITE(10L, "写作创作", 30, "思绪苇"),
    MUSIC(8L,  "音乐聆听", 20, "韵律兰"),

    // ===== 运动类 =====
    SPORT(3L,  "有氧运动", 30, "活力草"),
    DAILY(13L, "自律打卡", 15, "自律棘"),
    SLEEP(12L, "睡眠冥想", 20, "安眠星草"),

    // ===== 早起类 =====
    EARLY_RISE(4L, "早起打卡", 0, "晨光花"),
    PLAN(15L, "规划复盘", 20, "远见葵"),
    COMM(14L, "社交联结", 25, "联结藤"),

    // ===== 冥想类 =====
    MEDITATE(5L, "正念冥想", 10, "宁静叶"),
    TEA(16L,  "品茶静心", 20, "静心茗"),
    COOK(17L,  "烹饪专注", 30, "烟火椒"),

    // ===== 创造类 =====
    CREATE(7L, "创意构思", 25, "灵感菇"),
    TIDY(18L,  "整理收纳", 20, "秩序苔"),
    CUSTOM(6L, "自定义",   25, "星芽草");

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
