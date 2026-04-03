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
 * APM(Whatap/Jennifer) 호출건수 일별 수집 배치.
 */
@Component
public class ApmCollectJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ApmCollectJob.class);
    private final ScheduleConfigRepository scheduleRepo;

    public ApmCollectJob(ScheduleConfigRepository scheduleRepo) {
        this.scheduleRepo = scheduleRepo;
    }

    @Override
    public void execute(JobExecutionContext context) {
        String period = context.getJobDetail().getJobDataMap().getString("period"); // DAILY or WEEKLY
        log.info("[배치] APM 호출건수 수집 시작 (period={})", period);
        try {
            // TODO: 와탭/제니퍼 API 호출 → callCount/callCountMonth/callCountWeek 업데이트
            log.info("[배치] APM 수집 완료 (미구현 - 추후 연동)");
            updateResult(period, "성공");
        } catch (Exception e) {
            log.error("[배치] APM 수집 실패: {}", e.getMessage(), e);
            updateResult(period, "실패: " + e.getMessage());
        }
    }

    private void updateResult(String period, String result) {
        String jobType = "WEEKLY".equals(period) ? "APM_WEEKLY" : "APM_DAILY";
        scheduleRepo.findByJobType(jobType).ifPresent(c -> {
            c.setLastRunAt(LocalDateTime.now());
            c.setLastRunResult(result);
            scheduleRepo.save(c);
        });
    }
}
