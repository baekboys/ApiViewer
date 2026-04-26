package com.baek.viewer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 상태값 리네임 일회성 마이그레이션.
 *
 * 변환 이력 (모두 최종 형태로 수렴):
 *  1. 기존 "검토필요 차단대상"(아주 오래된 표기) → "검토필요대상"
 *  2. 기존 "추가검토필요 차단대상"(최근 표기) → "검토필요대상"
 *  3. 수동 판단 상태 통합 — 다음 3종을 단일 "차단대상 → 사용" 으로 통합
 *      · "최우선 차단대상 → 사용"
 *      · "후순위 차단대상 → 사용"
 *      · "현업요청 사용"
 *
 * 특성:
 * - Idempotent: 이미 변환된 상태면 0건 업데이트되고 로그만 남김.
 * - 커밋 방식: JdbcTemplate 기본 auto-commit 경로 (별도 트랜잭션 래핑 없음).
 * - `log_work_excluded` 컬럼 초기값은 false 로 Hibernate 가 컬럼 생성 시 설정 — 별도 백필 불필요.
 *   다음 분석/호출건수 반영 시 `ApiStorageService.calculateStatus` 가 올바르게 재계산한다.
 */
@Component
@Order(1)
public class StatusRenameMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StatusRenameMigrator.class);

    /** (newStatus, oldStatus) 매핑 — 순차 적용. 신규 변환 추가 시 이 배열에만 추가. */
    private static final String[][] RENAMES = {
            {"검토필요대상",     "검토필요 차단대상"},
            // NOTE: 한글 변환원본을 명시적으로 분리해 보관 (sed 자가 치환 방지)
            {"검토필요대상",     "추가" + "검토필요 차단대상"},
            {"차단대상 → 사용",  "최우선 차단대상 → 사용"},
            {"차단대상 → 사용",  "후순위 차단대상 → 사용"},
            {"차단대상 → 사용",  "현업요청 사용"},
    };

    private final JdbcTemplate jdbc;

    public StatusRenameMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        for (String[] r : RENAMES) {
            String newStatus = r[0];
            String oldStatus = r[1];
            try {
                int updated = jdbc.update(
                        "UPDATE api_record SET status = ? WHERE status = ?",
                        newStatus, oldStatus);
                if (updated > 0) {
                    log.warn("[마이그레이션] status '{}' → '{}' {}건 변환 완료", oldStatus, newStatus, updated);
                } else {
                    log.info("[마이그레이션] status '{}' 리네임 대상 없음 (이미 변환됨)", oldStatus);
                }
            } catch (Exception e) {
                // api_record 테이블이 아직 없거나(최초 기동 직전) DB 문제 — 기동은 계속 진행
                log.warn("[마이그레이션] status '{}' 리네임 스킵: {}", oldStatus, e.getMessage());
            }
        }
    }
}
