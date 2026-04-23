package com.baek.viewer.repository;

import com.baek.viewer.model.BatchExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BatchExecutionLogRepository extends JpaRepository<BatchExecutionLog, Long> {

    @Query("""
            SELECT b FROM BatchExecutionLog b
            WHERE b.startTime >= :from
              AND b.startTime < :to
              AND b.jobType IN :jobTypes
            ORDER BY b.startTime DESC
            """)
    List<BatchExecutionLog> findByRange(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("jobTypes") List<String> jobTypes);
}
