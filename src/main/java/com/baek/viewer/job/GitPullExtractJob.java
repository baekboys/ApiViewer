package com.baek.viewer.job;

import com.baek.viewer.model.ExtractRequest;
import com.baek.viewer.model.RepoConfig;
import com.baek.viewer.repository.RepoConfigRepository;
import com.baek.viewer.repository.ScheduleConfigRepository;
import com.baek.viewer.service.ApiExtractorService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Git Pull 후 전체 레포지토리 추출 배치.
 * 각 레포별로 git pull → 소스 분석 → DB 저장.
 */
public class GitPullExtractJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(GitPullExtractJob.class);
    @Autowired private ScheduleConfigRepository scheduleRepo;
    @Autowired private RepoConfigRepository repoConfigRepo;
    @Autowired private ApiExtractorService extractorService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("[배치] Git Pull & 추출 시작");
        List<RepoConfig> repos = repoConfigRepo.findAll();
        int success = 0, fail = 0;
        StringBuilder resultMsg = new StringBuilder();

        for (RepoConfig repo : repos) {
            if (!"Y".equalsIgnoreCase(repo.getAnalysisBatchEnabled())) {
                log.info("[배치] {} — 분석배치 비활성(N), 건너뜀", repo.getRepoName());
                continue;
            }
            if (repo.getRootPath() == null || repo.getRootPath().isBlank()) {
                log.warn("[배치] {} — rootPath 없음, 건너뜀", repo.getRepoName());
                continue;
            }

            try {
                // 1. Git 강제 동기화 (fetch + reset --hard + clean)
                String gitBin = (repo.getGitBinPath() != null && !repo.getGitBinPath().isBlank())
                        ? repo.getGitBinPath() : "git";
                String rootPath = repo.getRootPath();
                java.io.File gitDir = new java.io.File(rootPath);
                String branch = repo.getGitBranch();

                String syncStatus;
                String syncMessage;
                try {
                    log.info("[배치] {} — Git 강제 동기화 실행 (dir={}, branch={})",
                            repo.getRepoName(), rootPath, (branch == null || branch.isBlank()) ? "(HEAD)" : branch);
                    String syncResult = extractorService.hardSyncToOrigin(gitDir, gitBin, branch);
                    syncStatus = "OK";
                    syncMessage = syncResult;
                    log.info("[배치] {} — Git 동기화 완료: {}", repo.getRepoName(), syncResult);
                } catch (Exception syncEx) {
                    syncStatus = "FAIL";
                    syncMessage = syncEx.getMessage();
                    log.error("[배치] {} — Git 동기화 실패: {}", repo.getRepoName(), syncEx.getMessage());
                    resultMsg.append(repo.getRepoName()).append(":sync실패 ");
                }
                updateRepoSyncStatus(repo.getRepoName(), syncStatus, syncMessage);

                // 2. 추출 (동기화 실패해도 현재 파일 기준으로 분석 진행)
                ExtractRequest req = new ExtractRequest();
                req.setRootPath(rootPath);
                req.setRepositoryName(repo.getRepoName());
                req.setDomain(repo.getDomain());
                req.setApiPathPrefix(repo.getApiPathPrefix());
                req.setGitBinPath(gitBin);
                req.setPathConstants(repo.getPathConstants());
                req.setClientIp("BATCH");

                extractorService.extract(req);
                log.info("[배치] {} — 추출 완료", repo.getRepoName());
                success++;

            } catch (Exception e) {
                log.error("[배치] {} — 실패: {}", repo.getRepoName(), e.getMessage());
                resultMsg.append(repo.getRepoName()).append(":실패 ");
                fail++;
            }
        }

        String result = String.format("성공 %d개, 실패 %d개 / 총 %d개 레포", success, fail, repos.size());
        if (resultMsg.length() > 0) result += " (" + resultMsg.toString().trim() + ")";
        log.info("[배치] Git Pull & 추출 완료 — {}", result);
        updateResult(result);
    }

    /** 레포별 마지막 sync 결과를 repo_config 에 기록한다. 조회 화면 배너에서 참조.
     *  Quartz Job 이라 Spring AOP 기반 @Transactional 이 안정적이지 않으므로 생략.
     *  repoConfigRepo.save 는 Spring Data CrudRepository 가 이미 트랜잭션을 래핑한다. */
    protected void updateRepoSyncStatus(String repoName, String status, String message) {
        try {
            repoConfigRepo.findByRepoName(repoName).ifPresent(rc -> {
                rc.setLastSyncStatus(status);
                rc.setLastSyncAt(LocalDateTime.now());
                String trimmed = message;
                if (trimmed != null && trimmed.length() > 1000) {
                    trimmed = trimmed.substring(0, 1000) + "... (truncated)";
                }
                rc.setLastSyncMessage(trimmed);
                repoConfigRepo.save(rc);
            });
        } catch (Exception e) {
            log.warn("[배치] sync 상태 저장 실패 repo={}: {}", repoName, e.getMessage());
        }
    }

    private void updateResult(String result) {
        scheduleRepo.findByJobType("GIT_PULL_EXTRACT").ifPresent(c -> {
            c.setLastRunAt(LocalDateTime.now());
            c.setLastRunResult(result);
            scheduleRepo.save(c);
        });
    }
}
