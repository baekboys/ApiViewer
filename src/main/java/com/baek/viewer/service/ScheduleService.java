package com.baek.viewer.service;

import com.baek.viewer.job.ApmCollectJob;
import com.baek.viewer.job.GitPullExtractJob;
import com.baek.viewer.model.ScheduleConfig;
import com.baek.viewer.repository.ScheduleConfigRepository;
import jakarta.annotation.PostConstruct;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final Scheduler scheduler;
    private final ScheduleConfigRepository repository;

    public ScheduleService(Scheduler scheduler, ScheduleConfigRepository repository) {
        this.scheduler = scheduler;
        this.repository = repository;
    }

    /** 기동 시 기본 스케줄 등록 + DB에서 복원 */
    @PostConstruct
    public void init() {
        ensureDefaultConfigs();
        repository.findAll().forEach(this::applySchedule);
    }

    /** 기본 스케줄 설정이 없으면 생성 */
    private void ensureDefaultConfigs() {
        createIfAbsent("GIT_PULL_EXTRACT", "Git Pull & 소스 분석", "DAILY", "03:00");
        createIfAbsent("APM_DAILY", "APM 호출건수 일별 수집", "DAILY", "06:00");
        createIfAbsent("APM_WEEKLY", "APM 호출건수 주별 수집", "WEEKLY", "07:00");
    }

    private void createIfAbsent(String jobType, String desc, String scheduleType, String runTime) {
        if (repository.findByJobType(jobType).isEmpty()) {
            ScheduleConfig c = new ScheduleConfig();
            c.setJobType(jobType);
            c.setDescription(desc);
            c.setScheduleType(scheduleType);
            c.setRunTime(runTime);
            c.setEnabled(false);
            if ("WEEKLY".equals(scheduleType)) c.setRunDay("MON");
            repository.save(c);
        }
    }

    /** 스케줄 적용 (활성화면 등록, 비활성화면 삭제) */
    public void applySchedule(ScheduleConfig config) {
        try {
            JobKey jobKey = new JobKey(config.getJobType());
            TriggerKey triggerKey = new TriggerKey(config.getJobType() + "-trigger");

            // 기존 삭제
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }

            if (!config.isEnabled()) {
                log.info("[스케줄] {} 비활성화", config.getJobType());
                return;
            }

            Class<? extends Job> jobClass = resolveJobClass(config.getJobType());
            if (jobClass == null) return;

            String cron = config.toCronExpression();

            JobDetail job = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey)
                    .usingJobData("period", config.getJobType().contains("WEEKLY") ? "WEEKLY" : "DAILY")
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .build();

            scheduler.scheduleJob(job, trigger);
            log.info("[스케줄] {} 등록 완료: cron={}", config.getJobType(), cron);

        } catch (Exception e) {
            log.error("[스케줄] {} 등록 실패: {}", config.getJobType(), e.getMessage());
        }
    }

    /** 설정 저장 + 스케줄 재적용 */
    public ScheduleConfig saveAndApply(ScheduleConfig config) {
        ScheduleConfig saved = repository.save(config);
        applySchedule(saved);
        return saved;
    }

    public List<ScheduleConfig> findAll() {
        return repository.findAll();
    }

    private Class<? extends Job> resolveJobClass(String jobType) {
        return switch (jobType) {
            case "GIT_PULL_EXTRACT" -> GitPullExtractJob.class;
            case "APM_DAILY", "APM_WEEKLY" -> ApmCollectJob.class;
            default -> null;
        };
    }
}
