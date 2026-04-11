package com.baek.viewer.service;

import com.baek.viewer.model.WhatapRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WhatapService 단위테스트.
 * validate() 분기 + 실제 HTTP 호출은 없음.
 * fetchStats() 는 필수값 검증 실패 예외만 확인.
 */
class WhatapServiceTest {

    private final WhatapService service = new WhatapService();

    @Test
    @DisplayName("fetchStats — whatapUrl 이 null 이면 IllegalArgumentException")
    void fetchStats_nullUrl_throws() {
        WhatapRequest req = new WhatapRequest();
        req.setCookie("c");
        req.setStartDate("2024-01-01");
        req.setEndDate("2024-01-02");

        assertThatThrownBy(() -> service.fetchStats(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Whatap URL");
    }

    @Test
    @DisplayName("fetchStats — cookie 가 null 이면 IllegalArgumentException")
    void fetchStats_nullCookie_throws() {
        WhatapRequest req = new WhatapRequest();
        req.setWhatapUrl("http://test");
        req.setStartDate("2024-01-01");
        req.setEndDate("2024-01-02");

        assertThatThrownBy(() -> service.fetchStats(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠키");
    }

    @Test
    @DisplayName("fetchStats — startDate 누락 시 예외")
    void fetchStats_nullStartDate_throws() {
        WhatapRequest req = new WhatapRequest();
        req.setWhatapUrl("http://test");
        req.setCookie("c");
        req.setEndDate("2024-01-02");

        assertThatThrownBy(() -> service.fetchStats(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시작일");
    }

    @Test
    @DisplayName("fetchStats — endDate 누락 시 예외")
    void fetchStats_nullEndDate_throws() {
        WhatapRequest req = new WhatapRequest();
        req.setWhatapUrl("http://test");
        req.setCookie("c");
        req.setStartDate("2024-01-01");

        assertThatThrownBy(() -> service.fetchStats(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일");
    }

    @Test
    @DisplayName("fetchStats — 잘못된 날짜 포맷 시 예외")
    void fetchStats_invalidDateFormat_throws() {
        WhatapRequest req = new WhatapRequest();
        req.setWhatapUrl("http://localhost:0"); // 호출 시도 전에 파싱 실패
        req.setCookie("c");
        req.setStartDate("not-a-date");
        req.setEndDate("2024-01-02");

        assertThatThrownBy(() -> service.fetchStats(req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
