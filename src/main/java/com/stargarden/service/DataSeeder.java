package com.stargarden.service;

import com.stargarden.entity.FocusTask;
import com.stargarden.entity.FriendLike;
import com.stargarden.entity.Plant;
import com.stargarden.entity.TaskRecord;
import com.stargarden.entity.User;
import com.stargarden.repository.FriendLikeRepository;
import com.stargarden.repository.PlantRepository;
import com.stargarden.repository.TaskRecordRepository;
import com.stargarden.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 数据播种器：应用首次启动时初始化植物图鉴、管理员账号与模拟用户行为数据集。
 * 模拟数据集为混合推荐算法提供协同信号（用户-类别评分矩阵），
 * 并支撑离线评估（Precision@N / Recall@N 消融实验）。
 *
 * 与真实数据共存策略：
 * - 植物/管理员：仅在不存在的空库时创建，不触碰已有数据；
 * - 模拟用户 sim01~sim60：以「sim01 是否存在」为播种条件（而非记录表是否为空），
 *   因此真实用户的历史记录不会被清空或覆盖；
 * - 模拟行为记录的任务名取自任务大厅真实配置的专注任务，保证推荐结果可落地执行。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private PlantRepository plantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRecordRepository taskRecordRepository;
    @Autowired private FriendLikeRepository friendLikeRepository;
    @Autowired private FocusTaskService focusTaskService;
    @Autowired private PasswordEncoder passwordEncoder;

    /** 是否允许生成模拟数据集（application.properties: app.data.seed） */
    @Value("${app.data.seed:true}")
    private boolean seedEnabled;

    /** 模拟用户数量 */
    private static final int SIM_USERS = 60;
    /** 模拟行为回溯天数 */
    private static final int SIM_DAYS = 90;

    /** 固定随机种子，保证每次播种的数据分布可复现（论文实验可重复性） */
    private static final Random RANDOM = new Random(20233917L);

    /** 模拟用户偏好的时段：早鸟 6-9 / 上午 9-12 / 午后 13-17 / 晚间 19-23 */
    private static final int[][] TIME_SLOTS = {
            {6, 7, 8}, {9, 10, 11}, {13, 14, 15, 16}, {19, 20, 21, 22}
    };

    /** 时段索引 -> 该时段高频类别：模拟真实作息规律（早鸟爱运动早起、晚间爱阅读冥想），
     *  使时段与类别行为存在真实相关性——时段匹配因子的前提假设 */
    private static final Map<Integer, List<String>> SLOT_CATEGORIES = Map.of(
            0, List.of("早起", "运动", "学习"),
            1, List.of("学习", "工作", "早起"),
            2, List.of("阅读", "工作", "生活"),
            3, List.of("阅读", "冥想", "学习", "生活")
    );

    /** 类别 -> 默认植物ID 映射（任务大厅未配置该类别任务时的兜底，与初始图鉴一致） */
    private static final Map<String, Long> CATEGORY_PLANT = Map.of(
            "学习", 1L, "阅读", 2L, "运动", 3L, "早起", 4L, "冥想", 5L, "工作", 1L, "生活", 6L
    );

    @Override
    public void run(String... args) {
        seedPlants();
        seedAdmin();
        if (seedEnabled && userRepository.findByUsername("sim01").isEmpty()) {
            seedSimulationData();
        }
    }

    /** 植物图鉴为空时插入 6 种初始植物（空库兜底，已有图鉴时跳过） */
    private void seedPlants() {
        if (plantRepository.count() > 0) {
            return;
        }
        List<Plant> plants = List.of(
                plant("智慧藤", "完成学习任务获得", "COMMON", "🌿"),
                plant("专注花", "完成阅读任务获得", "COMMON", "🌸"),
                plant("活力草", "完成运动任务获得", "COMMON", "🍀"),
                plant("晨光花", "完成早起任务获得", "RARE", "🌅"),
                plant("宁静叶", "完成冥想任务获得", "COMMON", "🍃"),
                plant("星芽草", "默认植物", "COMMON", "🌱")
        );
        plantRepository.saveAll(plants);
        System.out.println("[DataSeeder] 已初始化植物图鉴 6 种");
    }

    private Plant plant(String name, String desc, String rarity, String icon) {
        Plant p = new Plant();
        p.setName(name);
        p.setDescription(desc);
        p.setRarity(rarity);
        p.setIcon(icon);
        return p;
    }

    /** 管理员账号不存在时创建 admin / admin123（BCrypt 加密存储） */
    private void seedAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setNickname("管理员");
        admin.setEmail("admin@stargarden.local");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole("ADMIN");
        userRepository.save(admin);
        System.out.println("[DataSeeder] 已创建管理员账号 admin / admin123");
    }

    /**
     * 生成模拟用户行为数据集：
     * 60 个模拟用户，每人具有 1-2 类偏好类别与 1 个偏好时段，
     * 在过去 90 天内随机产生专注记录（含噪声与遗忘行为），约 8000 条，
     * 并生成约 200 条好友点赞关系，用于社交关系分析。
     */
    private void seedSimulationData() {
        long start = System.currentTimeMillis();
        System.out.println("[DataSeeder] 开始生成模拟用户行为数据集（" + SIM_USERS + " 用户 x " + SIM_DAYS + " 天）...");

        List<String> categories = List.of("学习", "阅读", "运动", "早起", "冥想", "工作", "生活");
        // 类别 -> 任务大厅中该类别的启用任务（任务名/奖励植物均来自管理员真实配置）
        Map<String, List<FocusTask>> tasksByCategory = focusTaskService.listEnabled().stream()
                .collect(Collectors.groupingBy(t -> TaskCategoryUtil.derive(t.getTaskName())));

        List<User> users = new ArrayList<>();
        List<String[]> profiles = new ArrayList<>(); // 每人：偏好类别1、偏好类别2（可空）、时段索引

        // 1. 创建模拟用户
        for (int i = 1; i <= SIM_USERS; i++) {
            User u = new User();
            u.setUsername(String.format("sim%02d", i));
            u.setNickname("星芽体验官" + i);
            u.setEmail(String.format("sim%02d@stargarden.local", i));
            u.setPassword(passwordEncoder.encode("123456"));
            u.setRole("USER");
            u.setCreateTime(LocalDateTime.now().minusDays(SIM_DAYS + 5L));
            users.add(u);

            String c1 = categories.get(RANDOM.nextInt(categories.size()));
            String c2 = RANDOM.nextInt(100) < 45 ? categories.get(RANDOM.nextInt(categories.size())) : null;
            int slot = RANDOM.nextInt(TIME_SLOTS.length);
            profiles.add(new String[]{c1, c2, String.valueOf(slot)});
        }
        // 分块保存，避免一次性大批量 insert；返回值带回数据库生成的主键
        List<User> saved = saveUsersInChunks(users);

        // 2. 生成 90 天行为记录
        List<TaskRecord> records = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= SIM_USERS; i++) {
            long uid = saved.get(i - 1).getId(); // 主键由数据库生成，不假设从某值开始
            String[] profile = profiles.get(i - 1);
            String c1 = profile[0];
            String c2 = profile[1];
            int slotIdx = Integer.parseInt(profile[2]);
            int[] slotHours = TIME_SLOTS[slotIdx];

            for (int d = SIM_DAYS; d >= 1; d--) {
                // 每天 0-3 条记录，随坚持度变化，制造活跃度差异
                int n = RANDOM.nextInt(4);
                for (int k = 0; k < n; k++) {
                    String category;
                    // 类别生成：55% 时段典型行为（作息规律）+ 20% 个人偏好 + 15% 次偏好 + 10% 随机探索
                    int roll = RANDOM.nextInt(100);
                    List<String> slotCats = SLOT_CATEGORIES.get(slotIdx);
                    if (roll < 55) {
                        category = slotCats.get(RANDOM.nextInt(slotCats.size()));
                    } else if (roll < 75) {
                        category = c1;
                    } else if (roll < 90 && c2 != null) {
                        category = c2;
                    } else {
                        category = categories.get(RANDOM.nextInt(categories.size()));
                    }
                    // 80% 落在偏好时段，20% 全天随机（作息噪声）
                    int hour = RANDOM.nextInt(100) < 80
                            ? slotHours[RANDOM.nextInt(slotHours.length)]
                            : RANDOM.nextInt(24);
                    int minute = RANDOM.nextInt(60);
                    // 专注时长 15-100 分钟；早起打卡类为 0 分钟
                    int duration = "早起".equals(category) ? 0 : 15 + RANDOM.nextInt(86);
                    // 5% 概率早退，时长打折
                    if (duration > 20 && RANDOM.nextInt(100) < 5) {
                        duration = duration / 2;
                    }

                    // 该类别下随机选一个大厅真实任务（含奖励植物映射）
                    List<FocusTask> pool = tasksByCategory.getOrDefault(category, List.of());
                    FocusTask ft = pool.isEmpty() ? null : pool.get(RANDOM.nextInt(pool.size()));

                    TaskRecord r = new TaskRecord();
                    r.setUserId(uid);
                    r.setTaskName(ft != null ? ft.getTaskName() : poolTaskName(category));
                    r.setTaskCategory(category);
                    r.setDurationMinutes(duration);
                    r.setCompletedTime(today.minusDays(d).atTime(hour, minute));
                    r.setPlantId(ft != null && ft.getPlantId() != null
                            ? ft.getPlantId() : CATEGORY_PLANT.getOrDefault(category, 6L));
                    r.setPlanted(RANDOM.nextInt(100) < 70);
                    records.add(r);
                }
            }
        }
        saveRecordsInChunks(records);

        // 3. 生成约 200 条好友点赞关系（限定在模拟用户之间，不污染真实用户数据）
        List<FriendLike> likes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            long from = saved.get(RANDOM.nextInt(SIM_USERS)).getId();
            long to = saved.get(RANDOM.nextInt(SIM_USERS)).getId();
            if (from == to) {
                continue;
            }
            FriendLike like = new FriendLike();
            like.setFromUserId(from);
            like.setToUserId(to);
            like.setGardenViewTime(LocalDateTime.now().minusDays(RANDOM.nextInt(SIM_DAYS)));
            likes.add(like);
        }
        friendLikeRepository.saveAll(likes);

        System.out.printf("[DataSeeder] 模拟数据集生成完毕：用户 %d，行为记录 %d，好友点赞 %d，耗时 %d ms%n",
                users.size(), records.size(), likes.size(), System.currentTimeMillis() - start);
    }

    /** 大厅未配置该类别任务时，从内置任务池取一个具体任务名（完成时按任务名推导类别，链路闭环） */
    private String poolTaskName(String category) {
        List<String> pool = TaskCategoryUtil.CATEGORY_TASKS.getOrDefault(category, List.of("自由专注"));
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /** 用户分块写入（每 20 人一批），返回携带数据库主键的持久化实体 */
    private List<User> saveUsersInChunks(List<User> users) {
        List<User> saved = new ArrayList<>();
        for (int i = 0; i < users.size(); i += 20) {
            saved.addAll(userRepository.saveAll(users.subList(i, Math.min(users.size(), i + 20))));
        }
        return saved;
    }

    /** 记录分块写入（每 500 条一批） */
    private void saveRecordsInChunks(List<TaskRecord> records) {
        for (int i = 0; i < records.size(); i += 500) {
            taskRecordRepository.saveAll(records.subList(i, Math.min(records.size(), i + 500)));
        }
    }
}
