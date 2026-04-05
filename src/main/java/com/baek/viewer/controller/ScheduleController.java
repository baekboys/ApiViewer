package com.baek.viewer.controller;

import com.baek.viewer.model.ScheduleConfig;
import com.baek.viewer.repository.ScheduleConfigRepository;
import com.baek.viewer.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleConfigRepository scheduleRepo;

    public ScheduleController(ScheduleService scheduleService, ScheduleConfigRepository scheduleRepo) {
        this.scheduleService = scheduleService;
        this.scheduleRepo = scheduleRepo;
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
}
