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
}
