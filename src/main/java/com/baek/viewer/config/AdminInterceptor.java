package com.baek.viewer.config;

import com.baek.viewer.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AdminInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 요청은 통과
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        // GET /api/config/global, /api/config/repos 는 공개 (viewer에서 조회용)
        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod()) &&
                (uri.equals("/api/config/global") || uri.equals("/api/config/repos"))) {
            return true;
        }

        String token = request.getHeader("X-Admin-Token");
        if (authService.isValid(token)) return true;

        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"관리자 인증이 필요합니다.\"}");
        return false;
    }
}
