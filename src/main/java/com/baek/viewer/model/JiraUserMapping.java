package com.baek.viewer.model;

import jakarta.persistence.*;

@Entity
@Table(name = "jira_user_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_jira_user_mapping_team_name",
                columnNames = {"team_name", "urlviewer_name"}))
public class JiraUserMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 팀명 */
    @Column(name = "team_name", length = 100)
    private String teamName;

    /** URLViewer 담당자명 */
    @Column(name = "urlviewer_name", length = 100)
    private String urlviewerName;

    /** Jira 계정 ID (accountId) */
    @Column(name = "jira_account_id", length = 100)
    private String jiraAccountId;

    /** Jira 표시 이름 */
    @Column(name = "jira_display_name", length = 100)
    private String jiraDisplayName;

    // ── getter / setter ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getUrlviewerName() { return urlviewerName; }
    public void setUrlviewerName(String urlviewerName) { this.urlviewerName = urlviewerName; }

    public String getJiraAccountId() { return jiraAccountId; }
    public void setJiraAccountId(String jiraAccountId) { this.jiraAccountId = jiraAccountId; }

    public String getJiraDisplayName() { return jiraDisplayName; }
    public void setJiraDisplayName(String jiraDisplayName) { this.jiraDisplayName = jiraDisplayName; }
}
