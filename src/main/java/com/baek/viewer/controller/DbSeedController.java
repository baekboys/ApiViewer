package com.baek.viewer.controller;

import com.baek.viewer.service.TestDataSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 성능 테스트용 대량 더미 데이터 생성/정리 API (관리자 전용).
 *
 * 동기 호출 — 200만 건 규모는 수 분이 걸릴 수 있으므로, UI에서 오버레이로
 * 진행 중 상태를 표시하고 응답 완료까지 대기할 것.
 */
@RestController
@RequestMapping("/api/db/seed")
public class DbSeedController {

    private static final Logger log = LoggerFactory.getLogger(DbSeedController.class);

    private final TestDataSeedService seedService;

    public DbSeedController(TestDataSeedService seedService) {
        this.seedService = seedService;
    }

    /**
     * 테스트 데이터 생성.
     * @param apis  api_record 개수 (1~200,000)
     * @param days  APM 일수 (1~1000) — 총 APM 건수 = apis × days
     * @param clean 기존 test-repo-* 데이터 삭제 후 재생성 여부 (기본 true)
     */
    @PostMapping
    public ResponseEntity<?> seed(@RequestParam(defaultValue = "20000") int apis,
                                  @RequestParam(defaultValue = "100") int days,
                                  @RequestParam(defaultValue = "true") boolean clean) {
        if (apis < 1 || apis > 200_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "apis 범위: 1 ~ 200,000"));
        }
        if (days < 1 || days > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "days 범위: 1 ~ 1000"));
        }
        log.warn("[테스트시드 시작] apis={}, days={}, clean={}, 예상 apm={}건",
                apis, days, clean, (long) apis * days);
        try {
            Map<String, Object> result = seedService.seed(apis, days, clean);
            log.warn("[테스트시드 완료] {}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[테스트시드 실패] {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 테스트 데이터 정리 — repository_name LIKE 'test-repo-%' 전체 삭제. */
    @DeleteMapping
    public ResponseEntity<?> cleanup() {
        log.warn("[테스트시드 정리] DELETE /api/db/seed");
        try {
            return ResponseEntity.ok(seedService.cleanTestData());
        } catch (Exception e) {
            log.error("[테스트시드 정리 실패] {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
