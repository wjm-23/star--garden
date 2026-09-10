package com.stargarden.repository;

import com.stargarden.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    boolean existsByUserIdAndAchievementType(Long userId, String achievementType);
    List<Achievement> findByUserId(Long userId);
}