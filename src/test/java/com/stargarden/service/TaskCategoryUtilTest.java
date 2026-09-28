package com.stargarden.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 任务类别推导单元测试（类别体系：学习/阅读/运动/早起/冥想/工作/生活）
 */
class TaskCategoryUtilTest {

    @Test
    @DisplayName("关键词命中对应类别")
    void deriveByKeywords() {
        assertEquals("学习", TaskCategoryUtil.derive("考研英语单词"));
        assertEquals("学习", TaskCategoryUtil.derive("高数习题复习"));
        assertEquals("阅读", TaskCategoryUtil.derive("课外阅读"));
        assertEquals("阅读", TaskCategoryUtil.derive("文献精读"));
        assertEquals("运动", TaskCategoryUtil.derive("晨跑"));
        assertEquals("运动", TaskCategoryUtil.derive("健身训练"));
        assertEquals("早起", TaskCategoryUtil.derive("早起打卡"));
        assertEquals("冥想", TaskCategoryUtil.derive("睡前冥想"));
        assertEquals("工作", TaskCategoryUtil.derive("项目开发"));
        assertEquals("工作", TaskCategoryUtil.derive("实验报告"));
    }

    @Test
    @DisplayName("英文关键词与大小写兼容")
    void deriveEnglishCaseInsensitive() {
        assertEquals("学习", TaskCategoryUtil.derive("Study Session"));
        assertEquals("工作", TaskCategoryUtil.derive("write some CODE"));
    }

    @Test
    @DisplayName("无法识别或为空的任务名兜底归入「生活」")
    void deriveFallback() {
        assertEquals("生活", TaskCategoryUtil.derive("整理收纳"));
        assertEquals("生活", TaskCategoryUtil.derive("完全没见过的任务名"));
        assertEquals("生活", TaskCategoryUtil.derive(null));
    }
}
