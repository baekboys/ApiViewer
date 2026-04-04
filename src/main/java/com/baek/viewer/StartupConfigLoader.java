package com.baek.viewer;

import com.baek.viewer.service.YamlConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class StartupConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigLoader.class);

    private final YamlConfigService yamlConfigService;

    public StartupConfigLoader(YamlConfigService yamlConfigService) {
        this.yamlConfigService = yamlConfigService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        String path = yamlConfigService.getDefaultConfigPath();
        log.info("[기동 시 설정 로드] YAML 경로={}", path);
        if (!new File(path).exists()) {
            log.warn("[기동 시 설정 로드] repos-config.yml 없음, 자동 동기화 건너뜀: {}", path);
            return;
        }
        try {
            var result = yamlConfigService.importFromYaml(path);
            log.info("[기동 시 설정 로드] 동기화 완료 — 추가 {}개, 업데이트 {}개", result.get("added"), result.get("updated"));
        } catch (Exception e) {
            log.error("[기동 시 설정 로드] 동기화 실패: {}", e.getMessage(), e);
        }
    }
}
