package com.baek.viewer.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 측 관리자 토큰 관리.
 * 비밀번호 인증 성공 시 토큰 발급, 인터셉터에서 검증.
 */
@Service
public class AuthService {

    // token → 발급시각 (ms)
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = 8 * 60 * 60 * 1000; // 8시간

    /** 새 토큰 발급 */
    public String issueToken() {
        cleanup();
        String token = UUID.randomUUID().toString();
        tokens.put(token, System.currentTimeMillis());
        return token;
    }

    /** 토큰 유효성 검증 */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        Long issued = tokens.get(token);
        if (issued == null) return false;
        if (System.currentTimeMillis() - issued > TOKEN_TTL_MS) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    /** 토큰 폐기 (로그아웃) */
    public void revoke(String token) {
        if (token != null) tokens.remove(token);
    }

    /** 만료된 토큰 정리 */
    private void cleanup() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> now - e.getValue() > TOKEN_TTL_MS);
    }
}
