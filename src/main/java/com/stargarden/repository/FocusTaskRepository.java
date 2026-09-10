package com.stargarden.repository;

import com.stargarden.entity.FocusTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FocusTaskRepository extends JpaRepository<FocusTask, Long> {
    List<FocusTask> findByEnabledTrueOrderBySortOrderAsc();
    List<FocusTask> findAllByOrderBySortOrderAsc();
    FocusTask findByTaskName(String taskName);
}
