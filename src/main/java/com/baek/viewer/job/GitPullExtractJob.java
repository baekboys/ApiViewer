package com.baek.viewer.job;

import com.baek.viewer.model.ScheduleConfig;
import com.baek.viewer.repository.ScheduleConfigRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Git Pull 후 전체 레포지토리 추출 배치.
 * 각 레포별로 git pull → 소스 분석 → DB 저장.
 */
@Component
public class GitPullExtractJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(GitPullExtractJob.class);
    private final ScheduleConfigRepository scheduleRepo;

    public GitPullExtractJob(ScheduleConfigRepository scheduleRepo) {
        this.scheduleRepo = scheduleRepo;
    }

    @Override
    public void execute(JobExecutionContext context) {
        log.info("[배치] Git Pull & 추출 시작");
        try {
            // TODO: 각 레포별 git pull 실행 후 추출 로직 연동
            log.info("[배치] Git Pull & 추출 완료 (미구현 - 추후 연동)");
            updateResult("성공");
        } catch (Exception e) {
            log.error("[배치] Git Pull & 추출 실패: {}", e.getMessage(), e);
            updateResult("실패: " + e.getMessage());
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
