package com.baek.viewer.ai;

import com.baek.viewer.repository.AiPromptTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AiPromptTemplateSeed {

    private static final Logger log = LoggerFactory.getLogger(AiPromptTemplateSeed.class);

    private final AiPromptTemplateRepository repo;

    public AiPromptTemplateSeed(AiPromptTemplateRepository repo) {
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (repo.findBySlug(AiPromptSlugs.MENU_INFERENCE).isEmpty()) {
            repo.save(AiPromptTemplateDefaults.menuInference());
            log.info("[AI] 기본 프롬프트 템플릿 생성: {}", AiPromptSlugs.MENU_INFERENCE);
        }
        if (repo.findBySlug(AiPromptSlugs.OPS_DIGEST).isEmpty()) {
            repo.save(AiPromptTemplateDefaults.opsDigest());
            log.info("[AI] 기본 프롬프트 템플릿 생성: {}", AiPromptSlugs.OPS_DIGEST);
        }
    }
}
