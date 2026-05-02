package com.baek.viewer.model;

public class ExtractRequest {
    private String rootPath;
    private String repositoryName;
    private String domain;
    private String apiPathPrefix;
    private String pathConstants;
    private String gitBinPath;
    private String clientIp; // 서버에서 세팅
    /**
     * true면 이 Extract 호출 끝에서 자동 스냅샷 생성을 건너뛴다.
     * 여러 레포를 순차로 추출하는 경우에 사용 — 모든 레포가 끝난 뒤 호출자가 스냅샷을 1회만 생성한다.
     */
    private boolean skipSnapshot = false;

    public boolean isSkipSnapshot() { return skipSnapshot; }
    public void setSkipSnapshot(boolean skipSnapshot) { this.skipSnapshot = skipSnapshot; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }

    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getApiPathPrefix() { return apiPathPrefix; }
    public void setApiPathPrefix(String apiPathPrefix) { this.apiPathPrefix = apiPathPrefix; }

    public String getPathConstants() { return pathConstants; }
    public void setPathConstants(String pathConstants) { this.pathConstants = pathConstants; }

    public String getGitBinPath() { return gitBinPath; }
    public void setGitBinPath(String gitBinPath) { this.gitBinPath = gitBinPath; }
}