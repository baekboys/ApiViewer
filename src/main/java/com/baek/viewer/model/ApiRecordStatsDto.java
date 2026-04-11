package com.baek.viewer.model;

import java.time.LocalDateTime;

/**
 * 통계 집계 전용 경량 DTO.
 * TEXT 컬럼(fullComment, gitHistory 등) 제외 — 대용량 환경에서 메모리/IO 10~50배 절감.
 * 반드시 constructor expression @Query 로만 로드한다.
 */
public class ApiRecordStatsDto {

    private final Long id;
    private final String repositoryName;
    private final String status;
    private final String httpMethod;
    private final String teamOverride;
    private final String managerOverride;
    private final String apiPath;
    private final LocalDateTime lastAnalyzedAt;
    private final Boolean logWorkExcluded;

    public ApiRecordStatsDto(Long id,
                             String repositoryName,
                             String status,
                             String httpMethod,
                             String teamOverride,
                             String managerOverride,
                             String apiPath,
                             LocalDateTime lastAnalyzedAt,
                             Boolean logWorkExcluded) {
        this.id = id;
        this.repositoryName = repositoryName;
        this.status = status;
        this.httpMethod = httpMethod;
        this.teamOverride = teamOverride;
        this.managerOverride = managerOverride;
        this.apiPath = apiPath;
        this.lastAnalyzedAt = lastAnalyzedAt;
        this.logWorkExcluded = logWorkExcluded;
    }

    public Long getId() { return id; }
    public String getRepositoryName() { return repositoryName; }
    public String getStatus() { return status; }
    public String getHttpMethod() { return httpMethod; }
    public String getTeamOverride() { return teamOverride; }
    public String getManagerOverride() { return managerOverride; }
    public String getApiPath() { return apiPath; }
    public LocalDateTime getLastAnalyzedAt() { return lastAnalyzedAt; }
    /** 최우선 차단대상 중 "로그작업 이력 제외" 분류 여부 (subset 집계용). null → false 로 간주. */
    public boolean isLogWorkExcluded() { return logWorkExcluded != null && logWorkExcluded; }
}
