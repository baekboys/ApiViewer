package com.baek.viewer.repository;

import com.baek.viewer.model.ApiRecord;
import com.baek.viewer.model.ApiRecordSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApiRecordRepository extends JpaRepository<ApiRecord, Long> {

    List<ApiRecord> findByRepositoryName(String repositoryName);

    Optional<ApiRecord> findByRepositoryNameAndApiPathAndHttpMethod(
            String repositoryName, String apiPath, String httpMethod);

    @Query("SELECT DISTINCT r.repositoryName FROM ApiRecord r ORDER BY r.repositoryName")
    List<String> findAllRepositoryNames();

    List<ApiRecord> findByBlockTargetIsNotNull();

    // ── 경량 목록 조회 (fullComment, controllerComment, blockedReason 제외) ──
    @Query("SELECT r FROM ApiRecord r")
    List<ApiRecordSummary> findAllSummary();

    @Query("SELECT r FROM ApiRecord r WHERE r.repositoryName = :repo")
    List<ApiRecordSummary> findSummaryByRepositoryName(@Param("repo") String repositoryName);

    @Query("SELECT r FROM ApiRecord r WHERE r.blockTarget IS NOT NULL")
    List<ApiRecordSummary> findSummaryByBlockTargetIsNotNull();
}
