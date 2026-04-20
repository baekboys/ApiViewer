package com.baek.viewer.controller;

import com.baek.viewer.model.BlockedTxRow;
import com.baek.viewer.service.WhatapTxSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * URL 차단 모니터링 — /url-block-monitor.html 백엔드.
 * 공개 엔드포인트 (AdminInterceptor 미적용).
 */
@RestController
@RequestMapping("/api/url-block-monitor")
public class UrlBlockMonitorController {

    private static final Logger log = LoggerFactory.getLogger(UrlBlockMonitorController.class);

    private final WhatapTxSearchService txService;

    public UrlBlockMonitorController(WhatapTxSearchService txService) {
        this.txService = txService;
    }

    /** 활성 와탭 레포 + okind 매핑 — 화면 진입 시 드롭다운 옵션 구성용 */
    @GetMapping("/repos")
    public ResponseEntity<?> repos() {
        log.debug("[URL차단모니터] GET /repos");
        return ResponseEntity.ok(txService.listActiveRepos());
    }

    /** GlobalConfig에 저장된 봇 키워드 (콤마 구분) */
    @GetMapping("/bot-keywords")
    public ResponseEntity<?> botKeywords() {
        return ResponseEntity.ok(Map.of("keywords", txService.defaultBotKeywords()));
    }

    /** 검색 — body: {repoName, okindNames[], serviceLike, from(YYYY-MM-DD), to, excludeBot, extraBotKeywords[]} */
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchReq req) {
        log.info("[URL차단모니터] POST /search repoName={} from={} to={} excludeBot={} okindNames={} serviceLike={}",
                req.repoName, req.from, req.to, req.excludeBot, req.okindNames, req.serviceLike);
        try {
            LocalDate from = LocalDate.parse(req.from);
            LocalDate to = LocalDate.parse(req.to);
            List<BlockedTxRow> rows = txService.search(
                    req.repoName, req.okindNames, req.serviceLike, from, to,
                    req.excludeBot, req.extraBotKeywords);
            return ResponseEntity.ok(Map.of("total", rows.size(), "rows", rows));
        } catch (IllegalArgumentException e) {
            log.warn("[URL차단모니터] 입력 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("[URL차단모니터] 설정 오류: {}", e.getMessage());
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[URL차단모니터] 검색 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    public static class SearchReq {
        public String repoName;
        public List<String> okindNames;
        public String serviceLike;
        public String from;
        public String to;
        public boolean excludeBot = true;
        public List<String> extraBotKeywords;
    }
}
