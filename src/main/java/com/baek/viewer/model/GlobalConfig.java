package com.baek.viewer.model;

import jakarta.persistence.*;

@Entity
@Table(name = "global_config")
public class GlobalConfig {

    @Id
    private Long id = 1L; // 단일 레코드

    @Column(name = "start_date", length = 20)
    private String startDate;

    @Column(name = "end_date", length = 20)
    private String endDate;

    @Column(name = "review_threshold")
    private Integer reviewThreshold = 3;

    @Column(name = "password", length = 100)
    private String password;

    /** 팀 목록 JSON: ["IT카드개발팀","IT커머스개발팀"] — 자동완성용 */
    @Column(name = "teams", columnDefinition = "TEXT")
    private String teams;

    /** 와탭 공통 프로필 JSON: [{"name":"운영","url":"...","cookie":"..."}] */
    @Column(name = "whatap_profiles", columnDefinition = "TEXT")
    private String whatapProfiles;

    /** 제니퍼 공통 프로필 JSON: [{"name":"운영","url":"...","bearerToken":"..."}] */
    @Column(name = "jennifer_profiles", columnDefinition = "TEXT")
    private String jenniferProfiles;

    /** Whatap 실제 API 대신 Whatap 응답 스키마 형태의 Mock 데이터 사용 여부 */
    @Column(name = "whatap_mock_enabled")
    private Boolean whatapMockEnabled = false;

    /** Jennifer 실제 API 대신 Jennifer 응답 스키마 형태의 Mock 데이터 사용 여부 */
    @Column(name = "jennifer_mock_enabled")
    private Boolean jenniferMockEnabled = false;


    public Long getId() { return id; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Integer getReviewThreshold() { return reviewThreshold != null ? reviewThreshold : 3; }
    public void setReviewThreshold(Integer reviewThreshold) { this.reviewThreshold = reviewThreshold; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTeams() { return teams; }
    public void setTeams(String teams) { this.teams = teams; }
    public String getWhatapProfiles() { return whatapProfiles; }
    public void setWhatapProfiles(String whatapProfiles) { this.whatapProfiles = whatapProfiles; }
    public String getJenniferProfiles() { return jenniferProfiles; }
    public void setJenniferProfiles(String jenniferProfiles) { this.jenniferProfiles = jenniferProfiles; }
    public boolean isWhatapMockEnabled() { return Boolean.TRUE.equals(whatapMockEnabled); }
    public void setWhatapMockEnabled(Boolean whatapMockEnabled) { this.whatapMockEnabled = whatapMockEnabled; }
    public boolean isJenniferMockEnabled() { return Boolean.TRUE.equals(jenniferMockEnabled); }
    public void setJenniferMockEnabled(Boolean jenniferMockEnabled) { this.jenniferMockEnabled = jenniferMockEnabled; }
}
