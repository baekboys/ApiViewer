package com.baek.viewer.repository;

import com.baek.viewer.model.ApiRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ApiRecordRepository extends JpaRepository<ApiRecord, Long> {

    void deleteByExtractDateAndRepositoryName(LocalDate extractDate, String repositoryName);

    List<ApiRecord> findByExtractDateAndRepositoryName(LocalDate extractDate, String repositoryName);

    @Query("SELECT DISTINCT r.extractDate FROM ApiRecord r WHERE r.repositoryName = :repoName ORDER BY r.extractDate DESC")
    List<LocalDate> findDatesByRepositoryName(@Param("repoName") String repoName);

    @Query("SELECT DISTINCT r.repositoryName FROM ApiRecord r ORDER BY r.repositoryName")
    List<String> findAllRepositoryNames();
}
