package com.stargarden.repository;

import com.stargarden.entity.UserGarden;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserGardenRepository extends JpaRepository<UserGarden, Long> {
    List<UserGarden> findByUserId(Long userId);
    boolean existsByUserIdAndPositionXAndPositionY(Long userId, int x, int y);
    UserGarden findByUserIdAndPositionXAndPositionY(Long userId, int x, int y);
}