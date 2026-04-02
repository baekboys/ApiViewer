package com.baek.viewer;

import com.baek.viewer.service.YamlConfigService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class StartupConfigLoader {

    private final YamlConfigService yamlConfigService;

    public StartupConfigLoader(YamlConfigService yamlConfigService) {
        this.yamlConfigService = yamlConfigService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        String path = yamlConfigService.getDefaultConfigPath();
        if (!new File(path).exists()) {
            System.out.println("[ApiViewer] repos-config.yml 없음, 자동 동기화 건너뜀: " + path);
            return;
        }
        try {
            var result = yamlConfigService.importFromYaml(path);
            System.out.printf("[ApiViewer] repos-config.yml 동기화 완료 — 추가 %d개, 업데이트 %d개%n",
                    result.get("added"), result.get("updated"));
        } catch (Exception e) {
            System.err.println("[ApiViewer] repos-config.yml 동기화 실패: " + e.getMessage());
        }
    }
}
