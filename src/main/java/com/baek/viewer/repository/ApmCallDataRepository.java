package com.baek.viewer.repository;

import com.baek.viewer.model.ApmCallData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ApmCallDataRepository extends JpaRepository<ApmCallData, Long> {

    List<ApmCallData> findByRepositoryNameAndApiPathAndCallDate(
            String repositoryName, String apiPath, LocalDate callDate);

    /** (repo, apiPath, date, source) 단위 중복 체크 — source별로 별도 레코드 보관 */
    List<ApmCallData> findByRepositoryNameAndApiPathAndCallDateAndSource(
            String repositoryName, String apiPath, LocalDate callDate, String source);

    List<ApmCallData> findByRepositoryNameAndCallDateBetweenOrderByCallDateDesc(
            String repositoryName, LocalDate from, LocalDate to);

    /** 단일 API의 기간별 일자별 데이터 (차트용) */
    List<ApmCallData> findByRepositoryNameAndApiPathAndCallDateBetweenOrderByCallDateAsc(
            String repositoryName, String apiPath, LocalDate from, LocalDate to);

    List<ApmCallData> findByRepositoryNameAndSourceAndCallDateBetweenOrderByCallDateDesc(
            String repositoryName, String source, LocalDate from, LocalDate to);

    /**
     * 레포+API+날짜+source별 합계 (source별 중복 제거는 서비스 레이어에서 MAX로 처리).
     * 반환: [apiPath, callDate, source, callCount, errorCount]
     */
    @Query("SELECT a.apiPath, a.callDate, a.source, SUM(a.callCount), SUM(a.errorCount) " +
           "FROM ApmCallData a " +
           "WHERE a.repositoryName = :repo AND a.callDate BETWEEN :from AND :to " +
           "GROUP BY a.apiPath, a.callDate, a.source")
    List<Object[]> sumByRepoAndDateRange(@Param("repo") String repo,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    void deleteByRepositoryName(String repositoryName);

    void deleteByRepositoryNameAndSource(String repositoryName, String source);
}
