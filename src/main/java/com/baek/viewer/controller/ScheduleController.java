package com.baek.viewer.controller;

import com.baek.viewer.model.BatchDashboardDailyDto;
import com.baek.viewer.model.BatchExecutionLog;
import com.baek.viewer.model.ScheduleConfig;
import com.baek.viewer.repository.BatchExecutionLogRepository;
import com.baek.viewer.repository.ScheduleConfigRepository;
import com.baek.viewer.service.BatchDashboardHistoryService;
import com.baek.viewer.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleConfigRepository scheduleRepo;
    private final BatchExecutionLogRepository historyRepo;
    private final BatchDashboardHistoryService batchDashboardHistoryService;

    public ScheduleController(ScheduleService scheduleService,
                              ScheduleConfigRepository scheduleRepo,
                              BatchExecutionLogRepository historyRepo,
                              BatchDashboardHistoryService batchDashboardHistoryService) {
        this.scheduleService = scheduleService;
        this.scheduleRepo = scheduleRepo;
        this.historyRepo = historyRepo;
        this.batchDashboardHistoryService = batchDashboardHistoryService;
    }

    @GetMapping
    public ResponseEntity<List<ScheduleConfig>> list() {
        List<ScheduleConfig> list = scheduleService.findAll();
        if (list.isEmpty()) {
            // 기본 스케줄이 없으면 재생성
            scheduleService.ensureAndApplyDefaults();
            list = scheduleService.findAll();
        }
        return ResponseEntity.ok(list);
    }

    /** 기본 스케줄 강제 재생성 */
    @PostMapping("/reset")
    public ResponseEntity<List<ScheduleConfig>> reset() {
        scheduleService.ensureAndApplyDefaults();
        return ResponseEntity.ok(scheduleService.findAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ScheduleConfig existing = scheduleRepo.findById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        if (body.containsKey("enabled"))       existing.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("scheduleType"))  existing.setScheduleType((String) body.get("scheduleType"));
        if (body.containsKey("runTime"))       existing.setRunTime((String) body.get("runTime"));
        if (body.containsKey("runDay"))        existing.setRunDay((String) body.get("runDay"));
        if (body.containsKey("intervalHours")) existing.setIntervalHours(body.get("intervalHours") != null ? ((Number) body.get("intervalHours")).intValue() : null);
        if (body.containsKey("cronExpression"))existing.setCronExpression((String) body.get("cronExpression"));
        if (body.containsKey("jobParam"))      existing.setJobParam(body.get("jobParam") != null ? body.get("jobParam").toString() : null);

        return ResponseEntity.ok(scheduleService.saveAndApply(existing));
    }

    /**
     * 배치 수행 이력 조회.
     * @param from      YYYY-MM-DD (포함, 해당일 00:00)
     * @param to        YYYY-MM-DD (포함, 해당일 23:59:59.999)
     * @param jobTypes  콤마 구분 jobType 목록. 미지정 시 빈 결과 반환 (배치 선택 필수)
     */
    @GetMapping("/history")
    public ResponseEntity<List<BatchExecutionLog>> history(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String jobTypes) {
        if (jobTypes == null || jobTypes.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<String> types = Arrays.stream(jobTypes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (types.isEmpty()) return ResponseEntity.ok(List.of());

        LocalDateTime fromTs = LocalDate.parse(from).atStartOfDay();
        LocalDateTime toTs   = LocalDate.parse(to).plusDays(1).atStartOfDay();

        return ResponseEntity.ok(historyRepo.findByRange(fromTs, toTs, types));
    }

    /**
     * 대시보드용 배치 이력 — 공개 GET. 동일 (일자, jobType) 당 1행, 당일 다회 수행은 마지막 수행을 대표로 {@code runCount}에 횟수 표기.
     *
     * @param days 오늘 포함 조회 일 수 (기본 7, 최대 60)
     */
    @GetMapping("/history/dashboard-daily")
    public ResponseEntity<List<BatchDashboardDailyDto>> historyDashboardDaily(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(batchDashboardHistoryService.dailySummary(days));
    }
}

