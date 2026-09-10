package com.stargarden.repository;

import com.stargarden.entity.TaskRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRecordRepository extends JpaRepository<TaskRecord, Long> {
    List<TaskRecord> findByUserIdAndCompletedTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
    int countByUserIdAndCompletedTimeAfter(Long userId, LocalDateTime date);

    List<TaskRecord> findByUserId(Long userId);

    List<TaskRecord> findByUserIdOrderByCompletedTimeAsc(Long userId);

    /** 用户某天累计专注时长（协作房间好友 PK 使用） */
    @Query("select coalesce(sum(r.durationMinutes), 0) from TaskRecord r " +
           "where r.userId = :uid and r.completedTime >= :start and r.completedTime < :end")
    Long sumDurationByUserAndDay(@Param("uid") Long uid,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    List<TaskRecord> findByUserIdOrderByCompletedTimeDesc(Long userId);

    // 检查用户是否已经种过某种植物
    boolean existsByUserIdAndPlantIdAndPlanted(Long userId, Long plantId, boolean planted);

    // 查找用户未种植的某个植物记录
    TaskRecord findFirstByUserIdAndPlantIdAndPlantedOrderByCompletedTimeDesc(Long userId, Long plantId, boolean planted);

    // 检查用户是否有未种植的植物
    boolean existsByUserIdAndPlanted(Long userId, boolean planted);

    // 查找用户未种植的植物记录
    TaskRecord findFirstByUserIdAndPlantedOrderByCompletedTimeDesc(Long userId, boolean planted);

    // 查找用户全部未种植的植物记录（按获得时间升序）
    List<TaskRecord> findByUserIdAndPlantedOrderByCompletedTimeAsc(Long userId, boolean planted);
}
