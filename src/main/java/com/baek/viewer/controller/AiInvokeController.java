package com.baek.viewer.controller;

import com.baek.viewer.ai.AiMenuInferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiInvokeController {

    private static final Logger log = LoggerFactory.getLogger(AiInvokeController.class);

    private final AiMenuInferenceService menuInferenceService;

    public AiInvokeController(AiMenuInferenceService menuInferenceService) {
        this.menuInferenceService = menuInferenceService;
    }

    /**
     * 분석현황 레코드 단건 — 관련 메뉴(현업 설명) AI 제안
     */
    @PostMapping("/menu-suggestion")
    public ResponseEntity<?> menuSuggestion(@RequestBody Map<String, Object> body) {
        Object rid = body != null ? body.get("recordId") : null;
        long recordId;
        if (rid instanceof Number n) recordId = n.longValue();
        else if (rid != null) {
            try {
                recordId = Long.parseLong(rid.toString());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "recordId 가 올바르지 않습니다."));
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "recordId 필수"));
        }

        try {
            String suggestion = menuInferenceService.suggestMenuForRecord(recordId);
            return ResponseEntity.ok(Map.of("suggestion", suggestion != null ? suggestion : ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("[AI] menu-suggestion 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
