package com.baek.viewer.controller;

import com.baek.viewer.model.ApiInfo;
import com.baek.viewer.model.ApiRecord;
import com.baek.viewer.model.ExtractRequest;
import com.baek.viewer.model.WhatapRequest;
import com.baek.viewer.model.WhatapResult;
import com.baek.viewer.repository.ApiRecordRepository;
import com.baek.viewer.service.ApiExtractorService;
import com.baek.viewer.service.WhatapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiViewController {

    private final ApiExtractorService extractorService;
    private final WhatapService whatapService;
    private final ApiRecordRepository recordRepository;

    public ApiViewController(ApiExtractorService extractorService,
                             WhatapService whatapService,
                             ApiRecordRepository recordRepository) {
        this.extractorService = extractorService;
        this.whatapService = whatapService;
        this.recordRepository = recordRepository;
    }

    /** 추출 실행 (비동기) */
    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestBody ExtractRequest request) {
        try {
            extractorService.startExtractAsync(request);
            return ResponseEntity.accepted().body(Map.of("message", "추출 시작됨"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 추출 진행상황 조회 */
    @GetMapping("/progress")
    public ResponseEntity<?> progress() {
        return ResponseEntity.ok(extractorService.getProgress());
    }

    /** 캐시된 결과 조회 */
    @GetMapping("/list")
    public ResponseEntity<?> list() {
        List<ApiInfo> apis = extractorService.getCached();
        Map<String, Object> response = new HashMap<>();
        response.put("total", apis.size());
        response.put("deprecated", apis.stream().filter(a -> "Y".equals(a.getIsDeprecated())).count());
        response.put("apis", apis);
        return ResponseEntity.ok(response);
    }

    /** 추출 상태 확인 */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "extracting", extractorService.isExtracting(),
                "count", extractorService.getCached().size()
        ));
    }

    /** DB 저장된 레포지토리 목록 */
    @GetMapping("/db/repositories")
    public ResponseEntity<?> dbRepositories() {
        return ResponseEntity.ok(recordRepository.findAllRepositoryNames());
    }

    /** DB 저장된 추출 날짜 목록 */
    @GetMapping("/db/dates")
    public ResponseEntity<?> dbDates(@RequestParam String repository) {
        return ResponseEntity.ok(recordRepository.findDatesByRepositoryName(repository));
    }

    /** DB에서 API 목록 조회 */
    @GetMapping("/db/apis")
    public ResponseEntity<?> dbApis(@RequestParam String repository, @RequestParam String date) {
        LocalDate localDate = LocalDate.parse(date);
        List<ApiRecord> records = recordRepository.findByExtractDateAndRepositoryName(localDate, repository);
        Map<String, Object> response = new HashMap<>();
        response.put("total", records.size());
        response.put("deprecated", records.stream().filter(r -> "Y".equals(r.getIsDeprecated())).count());
        response.put("apis", records);
        return ResponseEntity.ok(response);
    }

    /** Whatap 호출건수 조회 */
    @PostMapping("/whatap/stats")
    public ResponseEntity<?> whatapStats(@RequestBody WhatapRequest request) {
        try {
            WhatapResult result = whatapService.fetchStats(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}