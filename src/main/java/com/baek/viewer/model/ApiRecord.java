package com.baek.viewer.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "api_record", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"extract_date", "repository_name", "api_path", "http_method"})
})
public class ApiRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extract_date", nullable = false)
    private LocalDate extractDate;

    @Column(name = "repository_name", nullable = false)
    private String repositoryName;

    @Column(name = "api_path", nullable = false, length = 500)
    private String apiPath;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "method_name")
    private String methodName;

    @Column(name = "controller_name")
    private String controllerName;

    @Column(name = "repo_path", length = 500)
    private String repoPath;

    @Column(name = "is_deprecated", length = 1)
    private String isDeprecated;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "api_operation_value", length = 500)
    private String apiOperationValue;

    @Column(name = "description_tag", length = 500)
    private String descriptionTag;

    @Column(name = "full_comment", columnDefinition = "TEXT")
    private String fullComment;

    @Column(name = "controller_comment", columnDefinition = "TEXT")
    private String controllerComment;

    @Column(name = "request_property_value", length = 500)
    private String requestPropertyValue;

    @Column(name = "controller_request_property_value", length = 500)
    private String controllerRequestPropertyValue;

    @Column(name = "full_url", length = 1000)
    private String fullUrl;

    @Column(name = "git_history", columnDefinition = "TEXT")
    private String gitHistory; // JSON: [{"date":"...","author":"...","message":"..."},...]

    public Long getId() { return id; }
    public LocalDate getExtractDate() { return extractDate; }
    public void setExtractDate(LocalDate extractDate) { this.extractDate = extractDate; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public String getApiPath() { return apiPath; }
    public void setApiPath(String apiPath) { this.apiPath = apiPath; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getControllerName() { return controllerName; }
    public void setControllerName(String controllerName) { this.controllerName = controllerName; }
    public String getRepoPath() { return repoPath; }
    public void setRepoPath(String repoPath) { this.repoPath = repoPath; }
    public String getIsDeprecated() { return isDeprecated; }
    public void setIsDeprecated(String isDeprecated) { this.isDeprecated = isDeprecated; }
    public String getProgramId() { return programId; }
    public void setProgramId(String programId) { this.programId = programId; }
    public String getApiOperationValue() { return apiOperationValue; }
    public void setApiOperationValue(String apiOperationValue) { this.apiOperationValue = apiOperationValue; }
    public String getDescriptionTag() { return descriptionTag; }
    public void setDescriptionTag(String descriptionTag) { this.descriptionTag = descriptionTag; }
    public String getFullComment() { return fullComment; }
    public void setFullComment(String fullComment) { this.fullComment = fullComment; }
    public String getControllerComment() { return controllerComment; }
    public void setControllerComment(String controllerComment) { this.controllerComment = controllerComment; }
    public String getRequestPropertyValue() { return requestPropertyValue; }
    public void setRequestPropertyValue(String requestPropertyValue) { this.requestPropertyValue = requestPropertyValue; }
    public String getControllerRequestPropertyValue() { return controllerRequestPropertyValue; }
    public void setControllerRequestPropertyValue(String v) { this.controllerRequestPropertyValue = v; }

    public String getFullUrl() { return fullUrl; }
    public void setFullUrl(String fullUrl) { this.fullUrl = fullUrl; }

    public String getGitHistory() { return gitHistory; }
    public void setGitHistory(String gitHistory) { this.gitHistory = gitHistory; }
}
