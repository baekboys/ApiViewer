package com.baek.viewer.controller;

import com.baek.viewer.model.ApiInfo;
import com.baek.viewer.model.ExtractRequest;
import com.baek.viewer.model.WhatapRequest;
import com.baek.viewer.model.WhatapResult;
import com.baek.viewer.service.ApiExtractorService;
import com.baek.viewer.service.WhatapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiViewController {

    private final ApiExtractorService extractorService;
    private final WhatapService whatapService;

    public ApiViewController(ApiExtractorService extractorService, WhatapService whatapService) {
        this.extractorService = extractorService;
        this.whatapService = whatapService;
    }

    /** 추출 실행 */
    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestBody ExtractRequest request) {
        try {
            List<ApiInfo> result = extractorService.extract(request);
            Map<String, Object> response = new HashMap<>();
            response.put("total", result.size());
            response.put("deprecated", result.stream().filter(a -> "Y".equals(a.getIsDeprecated())).count());
            response.put("apis", result);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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