package com.stargarden.service;

import com.stargarden.entity.TaskRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 植物生长阶段单元测试。
 * 规则：植物经验按「植物」独立累计，只有同一植物在「种下之后」获得的专注分钟才计入，
 * 因此刚种下必定是「发芽」，随后随同类专注成长（发芽→成长→开花）。
 */
class PlantGrowthStageTest {

    @Test
    @DisplayName("经验分钟数 → 生长阶段边界判定")
    void growthStageBoundaries() {
        assertEquals(0, GardenService.growthStageOf(0));
        assertEquals(0, GardenService.growthStageOf(GardenService.MINUTES_GROW - 1));
        assertEquals(1, GardenService.growthStageOf(GardenService.MINUTES_GROW));
        assertEquals(1, GardenService.growthStageOf(GardenService.MINUTES_BLOOM - 1));
        assertEquals(2, GardenService.growthStageOf(GardenService.MINUTES_BLOOM));
        assertEquals(2, GardenService.growthStageOf(GardenService.MINUTES_BLOOM + 1000));
    }

    @Test
    @DisplayName("生长经验只累计「同一植物」在「种下之后」的专注分钟")
    void growthMinutesOnlyCountSamePlantAfterPlanting() {
        LocalDateTime plantTime = LocalDateTime.of(2026, 1, 1, 10, 0);
        Long plantA = 1L;
        Long plantB = 2L;

        TaskRecord before = record(30, plantTime.minusDays(1), plantA); // 种下前：不计
        TaskRecord sameAfter = record(45, plantTime.plusDays(1), plantA); // 同植物、种下后：计
        TaskRecord otherAfter = record(100, plantTime.plusDays(2), plantB); // 不同植物：不计
        TaskRecord sameLater = record(20, plantTime.plusHours(3), plantA); // 同植物、种下后：计

        long xp = GardenService.plantGrowthMinutes(List.of(before, sameAfter, otherAfter, sameLater), plantA, plantTime);
        assertEquals(65, xp);

        // 刚种下（尚无后续同类专注）经验为 0 → 发芽
        assertEquals(0, GardenService.plantGrowthMinutes(List.of(before), plantA, plantTime));
    }

    @Test
    @DisplayName("阶段名称数组与阶段索引一一对应")
    void stageNamesMatchIndex() {
        assertEquals("发芽", GardenService.STAGE_NAMES[0]);
        assertEquals("成长", GardenService.STAGE_NAMES[1]);
        assertEquals("开花", GardenService.STAGE_NAMES[2]);
    }

    private static TaskRecord record(int minutes, LocalDateTime time, Long plantId) {
        TaskRecord r = new TaskRecord();
        r.setDurationMinutes(minutes);
        r.setCompletedTime(time);
        r.setPlantId(plantId);
        return r;
    }
}
