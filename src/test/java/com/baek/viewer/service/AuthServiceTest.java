package com.baek.viewer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthService 단위테스트 — 토큰 발급/검증/폐기 순수 로직.
 * 외부 의존성 없음. ConcurrentHashMap 기반.
 */
class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
    }

    @Test
    @DisplayName("issueToken — UUID 포맷의 새 토큰을 발급한다")
    void issueToken_returnsUuidToken() {
        String token = authService.issueToken();
        assertThat(token).isNotBlank();
        // UUID 포맷 검증: 8-4-4-4-12
        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("issueToken — 매번 다른 토큰을 발급한다")
    void issueToken_returnsUniqueTokens() {
        String t1 = authService.issueToken();
        String t2 = authService.issueToken();
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    @DisplayName("isValid — 방금 발급된 토큰은 유효하다")
    void isValid_returnsTrueForJustIssuedToken() {
        String token = authService.issueToken();
        assertThat(authService.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid — null/빈 문자열은 무효")
    void isValid_returnsFalseForNullOrBlank() {
        assertThat(authService.isValid(null)).isFalse();
        assertThat(authService.isValid("")).isFalse();
        assertThat(authService.isValid("   ")).isFalse();
    }

    @Test
    @DisplayName("isValid — 발급되지 않은 임의의 토큰은 무효")
    void isValid_returnsFalseForUnknownToken() {
        assertThat(authService.isValid("unknown-token-xyz")).isFalse();
    }

    @Test
    @DisplayName("revoke — 폐기된 토큰은 isValid에서 false")
    void revoke_invalidatesToken() {
        String token = authService.issueToken();
        assertThat(authService.isValid(token)).isTrue();

        authService.revoke(token);
        assertThat(authService.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("revoke — null 전달해도 예외 없음")
    void revoke_nullIsSafe() {
        authService.revoke(null); // no-op, no exception
    }
}
