package com.stargarden.repository;

import com.stargarden.entity.Plant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    @Query(value = "SELECT * FROM plant ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Plant findRandomPlant();

    List<Plant> findByRarity(String rarity);

    // 新增：根据植物名称查询
    Optional<Plant> findByName(String name);
}