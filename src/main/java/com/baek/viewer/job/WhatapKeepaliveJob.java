package com.baek.viewer.job;

import com.baek.viewer.model.RepoConfig;
import com.baek.viewer.repository.RepoConfigRepository;
import com.baek.viewer.repository.ScheduleConfigRepository;
import com.baek.viewer.service.WhatapTxSearchService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 와탭 쿠키 세션 keepalive 배치.
 * 활성 와탭 레포(whatapEnabled='Y' + whatapPcode 있음) 전체 순회 → WhatapTxSearchService.testConnection() 호출.
 * 목적: 세션 쿠키 유효 기간이 지나기 전에 주기적으로 flush를 보내 세션을 살려두는 것.
 * 응답 내용은 무관하며 HTTP 200 여부만 집계.
 */
public class WhatapKeepaliveJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(WhatapKeepaliveJob.class);

    @Autowired private ScheduleConfigRepository scheduleRepo;
    @Autowired private RepoConfigRepository repoConfigRepo;
    @Autowired private WhatapTxSearchService whatapTxSearchService;

    @Override
    public void execute(JobExecutionContext context) {
        long start = System.currentTimeMillis();
        log.info("────────────────────────────────────────");
        log.info("[WHATAP_KEEPALIVE] 시작");
        int total = 0, ok = 0, fail = 0;
        try {
            List<RepoConfig> repos = repoConfigRepo.findAll();
            for (RepoConfig r : repos) {
                if (!"Y".equalsIgnoreCase(r.getWhatapEnabled())) continue;
                if (r.getWhatapPcode() == null) continue;
                total++;
                try {
                    Map<String, Object> res = whatapTxSearchService.testConnection(r.getRepoName());
                    boolean isOk = Boolean.TRUE.equals(res.get("ok"));
                    if (isOk) {
                        ok++;
                        log.info("[WHATAP_KEEPALIVE]   - {} OK ({}ms)", r.getRepoName(), res.get("timeMs"));
                    } else {
                        fail++;
                        log.warn("[WHATAP_KEEPALIVE]   - {} 실패: {}", r.getRepoName(), res.get("message"));
                    }
                } catch (Exception e) {
                    fail++;
                    log.warn("[WHATAP_KEEPALIVE]   - {} 예외: {}", r.getRepoName(), e.getMessage());
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            String msg = String.format("성공 — 대상 %d개 / OK %d / 실패 %d / %dms", total, ok, fail, elapsed);
            log.info("[WHATAP_KEEPALIVE] 완료: {}", msg);
            updateResult(msg);
        } catch (Exception e) {
            log.error("[WHATAP_KEEPALIVE] 배치 실패: {}", e.getMessage(), e);
            updateResult("실패: " + e.getMessage());
        }
    }

    private void updateResult(String result) {
        scheduleRepo.findByJobType("WHATAP_KEEPALIVE").ifPresent(c -> {
            c.setLastRunAt(LocalDateTime.now());
            c.setLastRunResult(result);
            scheduleRepo.save(c);
        });
    }
}
