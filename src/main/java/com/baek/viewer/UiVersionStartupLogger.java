package com.baek.viewer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 서버 기동 완료 시 "현재 서빙되는 UI 버전"을 로그로 출력한다.
 * - DB 관리 없이, classpath 리소스(static/common/nav.js)에서 APP_UI_VERSION 값을 파싱한다.
 */
@Component
public class UiVersionStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(UiVersionStartupLogger.class);
    private final ObjectProvider<WebServerApplicationContext> webServerApplicationContextProvider;
    private final Environment env;

    private static final Pattern UI_VER_PATTERN =
            Pattern.compile("APP_UI_VERSION\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");

    public UiVersionStartupLogger(ObjectProvider<WebServerApplicationContext> webServerApplicationContextProvider,
                                  Environment env) {
        this.webServerApplicationContextProvider = webServerApplicationContextProvider;
        this.env = env;
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String uiVer = resolveUiVersionFromClasspath();
        if (uiVer == null || uiVer.isBlank()) uiVer = "(unknown)";
        log.info("[UI] 버전: {}", uiVer);

        logDbPathHints();

        int port = 8080;
        try {
            WebServerApplicationContext ctx = webServerApplicationContextProvider.getIfAvailable();
            if (ctx != null && ctx.getWebServer() != null) port = ctx.getWebServer().getPort();
        } catch (Exception ignored) {
            // ignore
        }

        String base = "http://localhost:" + port;
        log.info("[UI] 접속 링크:");
        log.info("[UI] - 대시보드: {}/dashboard/", base);
        log.info("[UI] - URL 분석현황: {}/url-viewer/viewer.html", base);
        log.info("[UI] - URL 호출현황: {}/url-viewer/call-stats.html", base);
        log.info("[UI] - 현업검토: {}/url-viewer/review.html", base);
        log.info("[UI] - 업무 플로우: {}/url-viewer/workflow.html", base);
        log.info("[UI] - URL차단 모니터링: {}/url-viewer/url-block-monitor.html", base);
        log.info("[UI] - 설정/데이터관리: {}/settings/", base);
        log.info("[UI] - URL 분석(추출): {}/url-viewer/extract.html", base);
        log.info("[UI] - H2 콘솔(관리자): {}/h2-console", base);
    }

    private void logDbPathHints() {
        try {
            String userDir = System.getProperty("user.dir");
            String url = env.getProperty("spring.datasource.url", "");
            log.info("[DB] user.dir={}", userDir);
            if (url == null || url.isBlank()) {
                log.info("[DB] datasource.url=(empty)");
                return;
            }
            log.info("[DB] datasource.url={}", url);

            String prefix = "jdbc:h2:file:";
            if (!url.startsWith(prefix)) return;

            String raw = url.substring(prefix.length());
            int semi = raw.indexOf(';');
            if (semi >= 0) raw = raw.substring(0, semi);
            raw = raw.trim();
            if (raw.isEmpty()) return;

            Path basePath = Paths.get(raw);
            if (!basePath.isAbsolute()) {
                basePath = Paths.get(userDir).resolve(basePath).normalize();
            }

            // H2는 보통 <path>.mv.db / <path>.trace.db 형태로 파일을 만든다.
            log.info("[DB] H2 file base={}", basePath);
            log.info("[DB] H2 file candidates: {}.mv.db , {}.trace.db", basePath, basePath);
        } catch (Exception e) {
            log.warn("[DB] 경로 힌트 출력 실패: {}", e.getMessage());
        }
    }

    private String resolveUiVersionFromClasspath() {
        try {
            ClassPathResource r = new ClassPathResource("static/common/nav.js");
            if (!r.exists()) return null;
            String text;
            try (InputStream is = r.getInputStream()) {
                text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher m = UI_VER_PATTERN.matcher(text);
            if (m.find()) return m.group(1).trim();
            return null;
        } catch (Exception e) {
            log.warn("[UI] 버전 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}

