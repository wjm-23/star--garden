package com.stargarden.service;

import java.util.List;
import java.util.Map;

/**
 * 任务类别（标签）工具：由任务名称推导类别，供推荐算法与数据统计使用。
 * 类别体系：学习 / 阅读 / 运动 / 早起 / 冥想 / 工作 / 生活
 */
public final class TaskCategoryUtil {

    private TaskCategoryUtil() { }

    /** 类别 -> 标签向量（任务标签相似度计算依据） */
    public static final Map<String, List<String>> CATEGORY_TAGS = Map.of(
            "学习", List.of("专注", "自我提升", "知识"),
            "阅读", List.of("专注", "放松", "知识"),
            "运动", List.of("健康", "活力", "减压"),
            "早起", List.of("健康", "自律"),
            "冥想", List.of("放松", "减压", "专注"),
            "工作", List.of("专注", "自我提升", "效率"),
            "生活", List.of("生活", "放松")
    );

    /** 类别 -> 建议任务名称池（推荐结果落地为具体任务） */
    public static final Map<String, List<String>> CATEGORY_TASKS = Map.of(
            "学习", List.of("深度学习", "考研英语单词", "高数习题", "论文写作"),
            "阅读", List.of("课外阅读", "文献阅读", "专业书精读"),
            "运动", List.of("晨跑", "健身训练", "球类运动"),
            "早起", List.of("早起打卡"),
            "冥想", List.of("睡前冥想", "午间冥想", "呼吸放松"),
            "工作", List.of("项目开发", "课程设计", "实验报告"),
            "生活", List.of("整理收纳", "规划明天")
    );

    /** 类别 -> 建议专注时长（分钟） */
    public static final Map<String, Integer> CATEGORY_MINUTES = Map.of(
            "学习", 45, "阅读", 30, "运动", 30, "早起", 0, "冥想", 10, "工作", 45, "生活", 20
    );

    /** 合法分类集合（顺序即前端筛选栏展示顺序），与 focus_task.category 取值一致 */
    public static final List<String> CATEGORIES = List.of(
            "学习", "阅读", "运动", "早起", "冥想", "工作", "生活"
    );

    /** 分类是否合法 */
    public static boolean isValid(String category) {
        return category != null && CATEGORIES.contains(category.trim());
    }

    /**
     * 解析任务分类：**优先使用任务上管理员配置的分类**（focus_task.category，单一事实来源），
     * 仅在未配置或非法时回退到按任务名关键词推导，避免"任务名猜测"造成标签与任务性质不符。
     */
    public static String resolve(String explicitCategory, String taskName) {
        return isValid(explicitCategory) ? explicitCategory.trim() : derive(taskName);
    }

    /**
     * 兜底：按关键词推导任务类别，无法识别时归入“生活”。
     * 与任务大厅既有任务名的归类保持一致（判断顺序：早起→冥想→运动→阅读→学习→工作→生活）。
     */
    public static String derive(String taskName) {
        if (taskName == null) return "生活";
        String n = taskName.toLowerCase();
        if (n.contains("早起") || n.contains("起床") || n.contains("自律")) return "早起";
        if (n.contains("冥想") || n.contains("正念") || n.contains("呼吸")
                || n.contains("睡眠") || n.contains("早睡")) return "冥想";
        if (n.contains("运动") || n.contains("有氧") || n.contains("跑") || n.contains("健身")
                || n.contains("锻炼") || n.contains("瑜伽") || n.contains("球")) return "运动";
        if (n.contains("阅读") || n.contains("读书") || n.contains("文献") || n.contains("看书")) return "阅读";
        if (n.contains("学习") || n.contains("复习") || n.contains("单词") || n.contains("作业")
                || n.contains("高数") || n.contains("网课") || n.contains("记忆") || n.contains("编程")
                || n.contains("study")) return "学习";
        if (n.contains("工作") || n.contains("开发") || n.contains("设计") || n.contains("报告")
                || n.contains("实验") || n.contains("项目") || n.contains("code")
                || n.contains("写作") || n.contains("创作") || n.contains("创意")
                || n.contains("规划") || n.contains("复盘")) return "工作";
        return "生活";
    }
}
