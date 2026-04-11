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
 * 배경: "검토필요 차단대상" → "추가검토필요 차단대상" 으로 상태값을 변경하면서,
 *       `ddl-auto=update` 만으로는 데이터 변환이 되지 않아 기동 시 1회 UPDATE 를 실행해야 한다.
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

    private final JdbcTemplate jdbc;

    public StatusRenameMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            int updated = jdbc.update(
                    "UPDATE api_record SET status = ? WHERE status = ?",
                    "추가검토필요 차단대상", "검토필요 차단대상");
            if (updated > 0) {
                log.warn("[마이그레이션] status '검토필요 차단대상' → '추가검토필요 차단대상' {}건 변환 완료", updated);
            } else {
                log.info("[마이그레이션] status 리네임 대상 없음 (이미 변환됨)");
            }
        } catch (Exception e) {
            // api_record 테이블이 아직 없거나(최초 기동 직전) DB 문제 — 기동은 계속 진행
            log.warn("[마이그레이션] status 리네임 스킵: {}", e.getMessage());
        }
    }
}
