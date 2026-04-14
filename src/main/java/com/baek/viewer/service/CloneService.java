package com.baek.viewer.service;

import com.baek.viewer.model.GlobalConfig;
import com.baek.viewer.repository.GlobalConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CloneService {

    private static final Logger log = LoggerFactory.getLogger(CloneService.class);
    private static final int MAX_CLONE_REPOS = 5;
    private static final ObjectMapper om = new ObjectMapper();

    // jobId -> 레포별 진행 상태 목록
    private final ConcurrentHashMap<String, List<RepoCloneStatus>> jobMap = new ConcurrentHashMap<>();

    private final GlobalConfigRepository globalRepo;

    public CloneService(GlobalConfigRepository globalRepo) {
        this.globalRepo = globalRepo;
    }

    // ── Bitbucket 레포 목록 조회 (페이지네이션) ──────────────────────────
    public Map<String, Object> listRepos(int page) throws Exception {
        GlobalConfig gc = globalRepo.findById(1L)
                .orElseThrow(() -> new IllegalStateException("설정 정보가 없습니다."));

        String baseUrl = gc.getBitbucketUrl();
        String token   = gc.getBitbucketToken();
        int limit      = gc.getListRepoLimit();

        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException("Bitbucket URL이 설정되지 않았습니다.");
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("Bitbucket 토큰이 설정되지 않았습니다.");

        int start = page * limit;
        String url = baseUrl.replaceAll("/+$", "")
                + "/rest/api/1.0/repos?limit=" + limit + "&start=" + start;

        log.info("[Bitbucket 레포 조회] page={} start={} url={}", page, start, url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("[Bitbucket API 오류] status={} body={}", response.statusCode(), response.body());
            throw new RuntimeException("Bitbucket API 오류 (HTTP " + response.statusCode() + ")");
        }

        Map<String, Object> result = om.readValue(response.body(), new TypeReference<>() {});
        log.info("[Bitbucket 레포 조회 완료] page={} isLastPage={}", page, result.get("isLastPage"));
        return result;
    }

    // ── Clone 실행 (비동기, 최대 5개) ────────────────────────────────────
    public String startClone(List<Map<String, String>> repos) {
        if (repos == null || repos.isEmpty())
            throw new IllegalArgumentException("클론할 레포지토리를 선택하세요.");
        if (repos.size() > MAX_CLONE_REPOS)
            throw new IllegalArgumentException("한 번에 최대 " + MAX_CLONE_REPOS + "개까지 클론 가능합니다.");

        GlobalConfig gc = globalRepo.findById(1L)
                .orElseThrow(() -> new IllegalStateException("설정 정보가 없습니다."));
        String localPath = gc.getCloneLocalPath();
        String token     = gc.getBitbucketToken();

        if (localPath == null || localPath.isBlank())
            throw new IllegalArgumentException("클론 로컬 경로가 설정되지 않았습니다.");

        String jobId = UUID.randomUUID().toString();
        List<RepoCloneStatus> statuses = new CopyOnWriteArrayList<>();

        for (Map<String, String> repo : repos) {
            RepoCloneStatus s = new RepoCloneStatus(repo.get("slug"), repo.get("cloneUrl"));
            statuses.add(s);
        }
        jobMap.put(jobId, statuses);

        for (RepoCloneStatus status : statuses) {
            CompletableFuture.runAsync(() -> runGitClone(status, localPath, token));
        }

        log.info("[Clone 시작] jobId={} repos={}", jobId, repos.stream().map(r -> r.get("slug")).toList());
        return jobId;
    }

    private void runGitClone(RepoCloneStatus status, String localPath, String token) {
        try {
            status.setStatus("CLONING");

            String cloneUrl = status.getCloneUrl();
            // HTTP URL에 토큰 삽입: http(s)://host/... -> http(s)://x-token-auth:TOKEN@host/...
            if (token != null && !token.isBlank() && cloneUrl != null && cloneUrl.startsWith("http")) {
                cloneUrl = cloneUrl.replaceFirst("://", "://x-token-auth:" + token + "@");
            }

            File targetDir = new File(localPath, status.getSlug());
            if (targetDir.exists()) {
                status.addLog("[SKIP] 이미 존재하는 디렉토리: " + targetDir.getAbsolutePath());
                status.setStatus("DONE");
                return;
            }

            new File(localPath).mkdirs();

            status.addLog("[시작] git clone -> " + targetDir.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--progress", cloneUrl,
                    targetDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 토큰이 로그에 노출되지 않도록 마스킹
                    if (token != null && !token.isBlank()) {
                        line = line.replace(token, "***");
                    }
                    status.addLog(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                status.setStatus("DONE");
                status.addLog("[완료] 클론 성공");
            } else {
                status.setStatus("ERROR");
                status.addLog("[실패] 종료 코드: " + exitCode);
            }
        } catch (Exception e) {
            status.setStatus("ERROR");
            status.addLog("[오류] " + e.getMessage());
            log.error("[Clone 오류][{}] {}", status.getSlug(), e.getMessage());
        }
    }

    public List<RepoCloneStatus> getJobStatus(String jobId) {
        return jobMap.get(jobId);
    }

    // ── sh 스크립트 생성 ─────────────────────────────────────────────────
    public String generateScript(List<Map<String, String>> repos) {
        GlobalConfig gc = globalRepo.findById(1L)
                .orElseThrow(() -> new IllegalStateException("설정 정보가 없습니다."));
        String localPath = gc.getCloneLocalPath() != null ? gc.getCloneLocalPath() : "";
        String token     = gc.getBitbucketToken()  != null ? gc.getBitbucketToken()  : "";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("# ─────────────────────────────────────────────────────────\n");
        sb.append("# Bitbucket Clone Script\n");
        sb.append("# 생성일시: ").append(timestamp).append("\n");
        sb.append("# 대상 레포: ").append(repos.size()).append("개\n");
        sb.append("# ─────────────────────────────────────────────────────────\n\n");
        sb.append("TOKEN=\"").append(token).append("\"\n");
        sb.append("BASE_DIR=\"").append(localPath).append("\"\n\n");
        sb.append("mkdir -p \"$BASE_DIR\"\n\n");

        for (int i = 0; i < repos.size(); i++) {
            Map<String, String> repo = repos.get(i);
            String slug     = repo.get("slug");
            String cloneUrl = repo.get("cloneUrl") != null ? repo.get("cloneUrl") : "";

            sb.append("# ").append(i + 1).append(". ").append(slug).append("\n");
            sb.append("echo '=== [").append(i + 1).append("/").append(repos.size())
              .append("] ").append(slug).append(" 클론 시작 ==='\n");
            sb.append("git clone --progress \\\n");
            sb.append("  \"$(echo '").append(cloneUrl)
              .append("' | sed 's|://|://x-token-auth:'\"$TOKEN\"'@|')\" \\\n");
            sb.append("  \"$BASE_DIR/").append(slug).append("\" 2>&1 \\\n");
            sb.append("  | grep -E '(Cloning|remote:|Receiving|Resolving|done\\.|error|fatal)'\n");
            sb.append("echo ''\n\n");
        }

        sb.append("echo '=== 전체 완료 ==='\n");
        return sb.toString();
    }

    // ── 내부 상태 클래스 ─────────────────────────────────────────────────
    public static class RepoCloneStatus {
        private final String slug;
        private final String cloneUrl;
        private volatile String status = "PENDING";
        private final List<String> logs = new CopyOnWriteArrayList<>();

        public RepoCloneStatus(String slug, String cloneUrl) {
            this.slug     = slug;
            this.cloneUrl = cloneUrl;
        }

        public void addLog(String line) { logs.add(line); }

        public String getSlug()     { return slug; }
        public String getCloneUrl() { return cloneUrl; }
        public String getStatus()   { return status; }
        public void setStatus(String s) { this.status = s; }
        public List<String> getLogs() { return new ArrayList<>(logs); }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug",   slug);
            m.put("status", status);
            m.put("logs",   getLogs());
            return m;
        }
    }
}
