package com.baek.viewer.repository;

import com.baek.viewer.model.ScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScheduleConfigRepository extends JpaRepository<ScheduleConfig, Long> {
    Optional<ScheduleConfig> findByJobType(String jobType);
}
