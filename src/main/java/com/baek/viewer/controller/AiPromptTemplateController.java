package com.baek.viewer.controller;

import com.baek.viewer.ai.AiPromptTestService;
import com.baek.viewer.model.AiPromptTemplate;
import com.baek.viewer.repository.AiPromptTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/config/ai/templates")
public class AiPromptTemplateController {

    private static final Logger log = LoggerFactory.getLogger(AiPromptTemplateController.class);

    private final AiPromptTemplateRepository repo;
    private final AiPromptTestService promptTestService;

    public AiPromptTemplateController(AiPromptTemplateRepository repo, AiPromptTestService promptTestService) {
        this.repo = repo;
        this.promptTestService = promptTestService;
    }

    @GetMapping
    public List<AiPromptTemplate> list() {
        log.info("[AI 템플릿] 목록 조회");
        return repo.findAllByOrderBySlugAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiPromptTemplate> get(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return repo.findById(id)
                .map(t -> {
                    if (body.containsKey("title")) {
                        Object v = body.get("title");
                        t.setTitle(v != null ? v.toString() : "");
                    }
                    if (body.containsKey("body")) {
                        Object v = body.get("body");
                        t.setBody(v != null ? v.toString() : "");
                    }
                    if (body.containsKey("enabled")) {
                        Object v = body.get("enabled");
                        if (v instanceof Boolean b) t.setEnabled(b);
                        else if (v != null) t.setEnabled(Boolean.parseBoolean(v.toString()));
                    }
                    AiPromptTemplate saved = repo.save(t);
                    log.info("[AI 템플릿] 수정 id={}, slug={}", id, saved.getSlug());
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String slug = body.get("slug") != null ? body.get("slug").toString().trim() : "";
        if (slug.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "slug 필수"));
        }
        if (repo.findBySlug(slug).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 존재하는 slug: " + slug));
        }
        AiPromptTemplate t = new AiPromptTemplate();
        t.setSlug(slug);
        t.setTitle(body.get("title") != null ? body.get("title").toString() : slug);
        t.setBody(body.get("body") != null ? body.get("body").toString() : "");
        Object en = body.get("enabled");
        t.setEnabled(en == null || Boolean.parseBoolean(en.toString()));
        t = repo.save(t);
        log.info("[AI 템플릿] 생성 slug={}", slug);
        return ResponseEntity.ok(t);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        log.info("[AI 템플릿] 삭제 id={}", id);
        return ResponseEntity.ok(Map.of("message", "삭제 완료"));
    }

    /**
     * 샘플 플레이스홀더로 본문을 치환한 뒤 사내 AI를 호출한다.
     * 요청 본문에 {@code body} 가 있으면 저장 전 편집 내용으로 시험한다.
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<?> testPrompt(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String override = null;
        if (body != null && body.get("body") != null) {
            override = body.get("body").toString();
        }
        try {
            Map<String, Object> result = promptTestService.runTest(id, override);
            log.info("[AI 템플릿] 테스트 id={}", id);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("[AI 템플릿] 테스트 실패 id={}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
