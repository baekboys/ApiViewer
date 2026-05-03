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

    /** 대시보드 일자별 집계용 — 전체 jobType, 시작 시각 내림차순 */
    @Query("""
            SELECT b FROM BatchExecutionLog b
            WHERE b.startTime >= :from AND b.startTime < :to
            ORDER BY b.startTime DESC
            """)
    List<BatchExecutionLog> findAllInRangeOrderByStartTimeDesc(@Param("from") LocalDateTime from,
                                                               @Param("to") LocalDateTime to);

    /** ops_digest AI 입력용 — 최근 이력 */
    List<BatchExecutionLog> findTop15ByOrderByIdDesc();
}
