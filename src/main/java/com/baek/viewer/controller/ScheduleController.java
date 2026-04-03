package com.baek.viewer.controller;

import com.baek.viewer.model.ScheduleConfig;
import com.baek.viewer.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResponseEntity<List<ScheduleConfig>> list() {
        return ResponseEntity.ok(scheduleService.findAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ScheduleConfig body) {
        body.setId(id);
        return ResponseEntity.ok(scheduleService.saveAndApply(body));
    }
}
