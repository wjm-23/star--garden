package com.stargarden.service;

import com.stargarden.entity.*;
import com.stargarden.enums.PlantTaskType;
import com.stargarden.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GardenService {

    private final PlantRepository plantRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final UserGardenRepository userGardenRepository;
    private final AchievementRepository achievementRepository;
    private final UserRepository userRepository;
    private final FocusTaskService focusTaskService;

    @Transactional
    public Plant completeTaskAndGetPlant(User user, String taskName, int durationMinutes) {
        // 优先取管理员在后台配置的任务->植物映射，退化到枚举默认值
        Long plantId;
        FocusTask ft = focusTaskService.findByTaskName(taskName);
        if (ft != null && ft.getPlantId() != null) {
            plantId = ft.getPlantId();
        } else {
            plantId = PlantTaskType.matchByTaskName(taskName).getPlantId();
        }
        Plant rewardPlant = plantRepository.findById(plantId).orElse(null);

        if (rewardPlant == null) {
            log.warn("植物ID={}不存在，退化为默认植物", plantId);
            rewardPlant = new Plant();
            rewardPlant.setId(1L);
            rewardPlant.setName(PlantTaskType.STUDY.getPlantName());
            rewardPlant.setRarity("COMMON");
        }

        log.info("用户 {} 完成任务 [{}]，获得植物: {}", user.getUsername(), taskName, rewardPlant.getName());

        TaskRecord record = new TaskRecord();
        record.setUserId(user.getId());
        record.setTaskName(taskName);
        // 类别标签：优先按管理员配置的任务归类，供推荐算法与数据报告统计
        record.setTaskCategory(TaskCategoryUtil.derive(taskName));
        record.setDurationMinutes(durationMinutes);
        record.setCompletedTime(LocalDateTime.now());
        record.setPlantId(rewardPlant.getId());
        record.setPlanted(false);
        taskRecordRepository.save(record);

        if (user.getTotalPlants() == null) user.setTotalPlants(0);
        if (user.getConsecutiveDays() == null) user.setConsecutiveDays(0);
        user.setTotalPlants(user.getTotalPlants() + 1);
        checkConsecutiveDays(user);
        checkAchievements(user);
        userRepository.save(user);

        return rewardPlant;
    }

    @Transactional
    public UserGarden plantToGarden(User user, Long plantId, int x, int y) {
        if (userGardenRepository.existsByUserIdAndPositionXAndPositionY(user.getId(), x, y)) {
            throw new RuntimeException("这个位置已经有植物了！");
        }
        TaskRecord record = taskRecordRepository.findFirstByUserIdAndPlantIdAndPlantedOrderByCompletedTimeDesc(
                user.getId(), plantId, false);
        if (record == null) {
            throw new RuntimeException("没有可种植的植物！先去完成任务获得新植物吧～");
        }
        record.setPlanted(true);
        taskRecordRepository.save(record);

        Plant plant = plantRepository.findById(plantId).orElse(null);
        if (plant == null) {
            throw new RuntimeException("植物不存在");
        }

        UserGarden garden = new UserGarden();
        garden.setUserId(user.getId());
        garden.setPlantId(plant.getId());
        garden.setPositionX(x);
        garden.setPositionY(y);
        garden.setPlantTime(LocalDateTime.now());

        log.info("用户 {} 在花园 [{},{}] 种植植物 {}", user.getUsername(), x, y, plant.getName());
        return userGardenRepository.save(garden);
    }

    /**
     * 铲除花园中某格的植物，腾出空格以便种植新种子。
     */
    @Transactional
    public void removePlant(User user, int x, int y) {
        UserGarden garden = userGardenRepository.findByUserIdAndPositionXAndPositionY(user.getId(), x, y);
        if (garden == null) {
            throw new RuntimeException("该位置没有植物哦～");
        }
        userGardenRepository.delete(garden);
        log.info("用户 {} 铲除花园 [{},{}] 的植物", user.getUsername(), x, y);
    }

    /**
     * 扩建花园：把网格边长 +1（6→7→…→10，上限 10×10）。
     * 条件由前端控制（需当前花园种满），此处仅负责持久化新尺寸。
     */
    @Transactional
    public int expandGarden(User user) {
        int size = (user.getGardenSize() == null) ? 6 : user.getGardenSize();
        if (size >= 10) {
            throw new RuntimeException("花园已达最大规模（10×10）啦～");
        }
        int newSize = size + 1;
        user.setGardenSize(newSize);
        userRepository.save(user);
        log.info("用户 {} 扩建花园至 {}×{}", user.getUsername(), newSize, newSize);
        return newSize;
    }

    private void checkConsecutiveDays(User user) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        int todayCount = taskRecordRepository.countByUserIdAndCompletedTimeAfter(user.getId(), todayStart);
        if (todayCount > 0) {
            LocalDateTime yesterdayStart = todayStart.minusDays(1);
            int yesterdayCount = taskRecordRepository.countByUserIdAndCompletedTimeAfter(user.getId(), yesterdayStart);
            user.setConsecutiveDays(yesterdayCount > 0 ? user.getConsecutiveDays() + 1 : 1);
        }
    }

    private void checkAchievements(User user) {
        if (user.getTotalPlants() == 1 && !hasAchievement(user, "FIRST_PLANT")) {
            addAchievement(user, "FIRST_PLANT");
        }
        if (user.getConsecutiveDays() >= 7 && !hasAchievement(user, "SEVEN_DAYS")) {
            addAchievement(user, "SEVEN_DAYS");
        }
        if (user.getTotalPlants() >= 30 && !hasAchievement(user, "THIRTY_PLANTS")) {
            addAchievement(user, "THIRTY_PLANTS");
        }
    }

    private boolean hasAchievement(User user, String type) {
        return achievementRepository.existsByUserIdAndAchievementType(user.getId(), type);
    }

    private void addAchievement(User user, String type) {
        Achievement achievement = new Achievement();
        achievement.setUserId(user.getId());
        achievement.setAchievementType(type);
        achievement.setAchievedTime(LocalDateTime.now());
        achievementRepository.save(achievement);
        log.info("用户 {} 解锁成就: {}", user.getUsername(), type);
    }

    /**
     * 修复 N+1：一次性批量查 plant 信息，再组装 map
     */
    /**
     * 返回用户「待种植」的种子背包：把未种植(planted=false)的专注记录按植物聚合，
     * 这样用户随时可以进入花园挑选种子种植，不必在获得时立刻种下。
     */
    public List<Map<String, Object>> getPendingPlants(User user) {
        List<TaskRecord> pending = taskRecordRepository
                .findByUserIdAndPlantedOrderByCompletedTimeAsc(user.getId(), false);
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (TaskRecord r : pending) {
            if (r.getPlantId() == null) continue;
            counts.put(r.getPlantId(), counts.getOrDefault(r.getPlantId(), 0) + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            Plant p = plantRepository.findById(e.getKey()).orElse(null);
            if (p == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("plantId", p.getId());
            m.put("plantName", p.getName());
            m.put("rarity", p.getRarity());
            m.put("count", e.getValue());
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> getUserGardenMap(User user) {
        List<UserGarden> gardens = userGardenRepository.findByUserId(user.getId());
        Map<String, Object> result = new HashMap<>();
        if (gardens.isEmpty()) return result;

        Set<Long> plantIds = gardens.stream()
                .map(UserGarden::getPlantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 批量查询，只查一次 DB
        Map<Long, Plant> plantMap = plantRepository.findAllById(plantIds).stream()
                .collect(Collectors.toMap(Plant::getId, p -> p));

        for (UserGarden g : gardens) {
            String key = g.getPositionX() + "," + g.getPositionY();
            Plant plant = plantMap.get(g.getPlantId());
            if (plant != null) {
                Map<String, Object> plantInfo = new HashMap<>();
                plantInfo.put("name", plant.getName());
                plantInfo.put("rarity", plant.getRarity());
                plantInfo.put("imageUrl", plant.getImageUrl());
                result.put(key, plantInfo);
            }
        }
        return result;
    }

    public List<Achievement> getUserAchievements(User user) {
        return achievementRepository.findByUserId(user.getId());
    }

    public List<TaskRecord> getUserRecentTasks(User user) {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        return taskRecordRepository.findByUserIdAndCompletedTimeBetween(user.getId(), start, LocalDateTime.now());
    }
}
