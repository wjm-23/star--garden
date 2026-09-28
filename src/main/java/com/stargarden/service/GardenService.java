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
    private final FriendLikeRepository friendLikeRepository;

    // ==================== 植物生长阶段（开题报告：植物随专注积累呈现发芽→成长→开花变化） ====================
    /**
     * 生长经验（= 该植物累计获得的同类专注分钟）按「植物」独立累计：
     * 只有继续完成同一株植物的专注任务，才会给这株植物加经验，不同植物之间的经验互不相通
     * （对应"同一植物才能发芽成长生花，相当于涨任务经验"）。
     * 经验从「该植株种下之后」开始计算，因此刚种下时必定是「发芽」，随后随同类专注逐步成长。
     */
    public static final int MINUTES_GROW = 30;    // 同类专注累计 ≥30 分钟 → 「成长」
    public static final int MINUTES_BLOOM = 120;  // 同类专注累计 ≥120 分钟 → 「开花」
    public static final String[] STAGE_NAMES = {"发芽", "成长", "开花"};

    /** 按经验分钟数换算生长阶段：0=发芽 1=成长 2=开花 */
    public static int growthStageOf(long growthMinutes) {
        if (growthMinutes >= MINUTES_BLOOM) return 2;
        if (growthMinutes >= MINUTES_GROW) return 1;
        return 0;
    }

    /**
     * 该植株自「种下时刻」起累计获得的专注分钟数（即这株植物的"任务经验"）。
     * 只统计 plantId 相同、且 completedTime 晚于种下时刻的记录——于是：
     * 刚种下=0 经验（发芽），之后每完成一次同类植物的任务都会为它累加经验。
     */
    public static long plantGrowthMinutes(List<TaskRecord> records, Long plantId, LocalDateTime plantTime) {
        if (plantId == null) return 0;
        LocalDateTime since = plantTime == null ? LocalDateTime.now() : plantTime;
        long sum = 0;
        for (TaskRecord r : records) {
            if (!Objects.equals(plantId, r.getPlantId())) continue;
            if (r.getCompletedTime() == null || !r.getCompletedTime().isAfter(since)) continue;
            sum += r.getDurationMinutes() == null ? 0 : r.getDurationMinutes();
        }
        return sum;
    }

    /** 单株植物的生长阶段（基于该植物自身的经验，而非用户全局累计） */
    public int stageForCell(List<TaskRecord> records, Long plantId, LocalDateTime plantTime) {
        return growthStageOf(plantGrowthMinutes(records, plantId, plantTime));
    }

    /**
     * 某格植株的「总成长经验」= 完成任务获得的同类专注分钟 + 格子内浇水培育追加的 minutes。
     * 这样即使不离开花园，也能在同一格子里通过浇水让植物从发芽→成长→开花。
     */
    public long cellGrowthMinutes(List<TaskRecord> records, UserGarden g) {
        long focus = plantGrowthMinutes(records, g.getPlantId(), g.getPlantTime());
        int bonus = g.getGrowthBonus() == null ? 0 : g.getGrowthBonus();
        return focus + bonus;
    }

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
        // 类别标签：优先取任务自身配置的分类（focus_task.category），未配置才按名称兜底推导
        record.setTaskCategory(TaskCategoryUtil.resolve(ft == null ? null : ft.getCategory(), taskName));
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
     * 在同一格子里「再种一次相同植物」= 让该植株成长一阶：
     * 第 1 次种下→发芽；第 2 次同株再种→成长；第 3 次同株再种→开花。
     * 与"种植"流程一致，本次调用必须传入一颗与格内「同种」的种子（plantId 相等），
     * 并消耗背包里一颗该植物的种子；若种子种类不符或无库存则直接拒绝。
     * 通过给该格追加刚好跨过下一阶段阈值的成长经验实现，已开花则不再成长。
     * 返回最新阶段信息，供前端即时刷新。
     */
    @Transactional
    public Map<String, Object> growPlant(User user, int x, int y, Long plantId) {
        UserGarden garden = userGardenRepository.findByUserIdAndPositionXAndPositionY(user.getId(), x, y);
        if (garden == null) {
            throw new RuntimeException("该位置没有植物哦～");
        }
        // 硬校验：选中的种子必须与格内植物同一种
        if (plantId == null || !plantId.equals(garden.getPlantId())) {
            throw new RuntimeException("请先在背包选中与本格相同的「" + plantName(garden.getPlantId()) + "」种子，再点种植～");
        }
        // 消耗背包里一颗同种植物的种子（与种植流程一致）
        TaskRecord record = taskRecordRepository.findFirstByUserIdAndPlantIdAndPlantedOrderByCompletedTimeDesc(
                user.getId(), plantId, false);
        if (record == null) {
            throw new RuntimeException("你还没有「" + plantName(plantId) + "」的种子了，先去完成任务获得吧～");
        }
        record.setPlanted(true);
        taskRecordRepository.save(record);

        List<TaskRecord> records = taskRecordRepository.findByUserId(user.getId());
        long xp = cellGrowthMinutes(records, garden);
        int stage = growthStageOf(xp);
        if (stage >= 2) {
            throw new RuntimeException("这株已经盛开了～");
        }
        // 追加刚好让它进入下一阶段的经验（发芽30 / 开花120）
        long target = stage == 0 ? MINUTES_GROW : MINUTES_BLOOM;
        long needed = target - xp;
        if (needed <= 0) needed = 1;
        garden.setGrowthBonus((garden.getGrowthBonus() == null ? 0 : garden.getGrowthBonus()) + (int) needed);
        userGardenRepository.save(garden);

        long newXp = cellGrowthMinutes(records, garden);
        int newStage = growthStageOf(newXp);
        long next = newStage == 0 ? MINUTES_GROW : (newStage == 1 ? MINUTES_BLOOM : -1);
        Map<String, Object> res = new HashMap<>();
        res.put("xpMinutes", newXp);
        res.put("nextStageMinutes", next);
        res.put("stage", newStage);
        res.put("stageName", STAGE_NAMES[newStage]);
        log.info("用户 {} 同格再种花园 [{},{}]，当前阶段 {}", user.getUsername(), x, y, STAGE_NAMES[newStage]);
        return res;
    }

    private String plantName(Long id) {
        if (id == null) return "该植物";
        Plant p = plantRepository.findById(id).orElse(null);
        return p == null ? "该植物" : p.getName();
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

    /**
     * 依据用户「真实数据」推导应当解锁的成就集合：
     * 累计植物数 / 连续打卡天数 / 点赞与被点赞次数，是成就的唯一事实来源。
     * 供成就接口实时返回，保证任何账号（含模拟演示账号）看到的成就都与自身数据一致。
     */
    public Set<String> deriveAchievementTypes(User user) {
        Set<String> set = new LinkedHashSet<>();
        if (user == null) return set;
        int total = user.getTotalPlants() == null ? 0 : user.getTotalPlants();
        int days = user.getConsecutiveDays() == null ? 0 : user.getConsecutiveDays();
        int given = friendLikeRepository.countByFromUserId(user.getId());
        int received = friendLikeRepository.countByToUserId(user.getId());
        if (total >= 1) set.add("FIRST_PLANT");        // 种下第一株
        if (days >= 7) set.add("SEVEN_DAYS");          // 连续打卡 7 天
        if (total >= 30) set.add("THIRTY_PLANTS");     // 累计 30 株
        if (total >= 60) set.add("GREEN_THUMB");       // 累计 60 株
        if (given >= 1) set.add("FIRST_LIKE");         // 首次点赞
        if (given >= 10) set.add("SOCIAL_BUTTERFLY");  // 点赞 10 次
        if (received >= 1) set.add("FIRST_LIKED");     // 首次被赞
        if (received >= 20) set.add("POPULAR_GARDENER"); // 被赞 20 次
        return set;
    }

    /** 把用户应得的成就补齐落库（幂等），使 achievement 表与真实数据保持一致 */
    public void syncAchievements(User user) {
        for (String type : deriveAchievementTypes(user)) {
            if (!hasAchievement(user, type)) addAchievement(user, type);
        }
    }

    private void checkAchievements(User user) {
        syncAchievements(user);
    }

    /** 点赞相关成就：首次点赞 / 累计点赞10次 / 首次被赞 / 累计被赞20次 */
    public void checkLikeAchievement(User user) {
        syncAchievements(user);
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

        // 生长阶段依据：该用户全部专注记录（累计时长随种下时刻定格）
        List<TaskRecord> records = taskRecordRepository.findByUserId(user.getId());

        for (UserGarden g : gardens) {
            String key = g.getPositionX() + "," + g.getPositionY();
            Plant plant = plantMap.get(g.getPlantId());
            if (plant != null) {
                Map<String, Object> plantInfo = new HashMap<>();
                plantInfo.put("name", plant.getName());
                plantInfo.put("plantId", g.getPlantId());
                plantInfo.put("rarity", plant.getRarity());
                plantInfo.put("imageUrl", plant.getImageUrl());
                plantInfo.put("icon", plant.getIcon());
                int stage = growthStageOf(cellGrowthMinutes(records, g));
                plantInfo.put("stage", stage);
                plantInfo.put("stageName", STAGE_NAMES[stage]);
                // 还差多少分钟经验升到下一阶段（供前端展示成长进度）
                long xp = cellGrowthMinutes(records, g);
                long next = stage == 0 ? MINUTES_GROW : (stage == 1 ? MINUTES_BLOOM : -1);
                plantInfo.put("xpMinutes", xp);
                plantInfo.put("nextStageMinutes", next);
                result.put(key, plantInfo);
            }
        }
        return result;
    }

    /** 单株植物生长阶段（查库版，供控制器/其他服务直接调用） */
    public int stageForPlant(Long userId, Long plantId, LocalDateTime plantTime) {
        List<TaskRecord> records = taskRecordRepository.findByUserId(userId);
        return stageForCell(records, plantId, plantTime);
    }

    public List<Achievement> getUserAchievements(User user) {
        return achievementRepository.findByUserId(user.getId());
    }

    public List<TaskRecord> getUserRecentTasks(User user) {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        return taskRecordRepository.findByUserIdAndCompletedTimeBetween(user.getId(), start, LocalDateTime.now());
    }
}
