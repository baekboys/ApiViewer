package com.baek.viewer.controller;

import com.baek.viewer.model.GlobalConfig;
import com.baek.viewer.model.RepoConfig;
import com.baek.viewer.repository.GlobalConfigRepository;
import com.baek.viewer.repository.RepoConfigRepository;
import com.baek.viewer.service.YamlConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final RepoConfigRepository repoRepo;
    private final GlobalConfigRepository globalRepo;
    private final YamlConfigService yamlConfigService;

    public ConfigController(RepoConfigRepository repoRepo,
                            GlobalConfigRepository globalRepo,
                            YamlConfigService yamlConfigService) {
        this.repoRepo = repoRepo;
        this.globalRepo = globalRepo;
        this.yamlConfigService = yamlConfigService;
    }

    // ── 공통 설정 ──────────────────────────────────────────
    @GetMapping("/global")
    public ResponseEntity<?> getGlobal() {
        return ResponseEntity.ok(globalRepo.findById(1L).orElse(new GlobalConfig()));
    }

    @PutMapping("/global")
    public ResponseEntity<?> saveGlobal(@RequestBody GlobalConfig config) {
        config = globalRepo.save(config);
        return ResponseEntity.ok(config);
    }

    // ── 레포 설정 목록 ────────────────────────────────────
    @GetMapping("/repos")
    public ResponseEntity<?> listRepos() {
        return ResponseEntity.ok(repoRepo.findAllByOrderByRepoNameAsc());
    }

    // ── 레포 단건 조회 ────────────────────────────────────
    @GetMapping("/repos/{id}")
    public ResponseEntity<?> getRepo(@PathVariable Long id) {
        return repoRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── 레포 추가 ─────────────────────────────────────────
    @PostMapping("/repos")
    public ResponseEntity<?> createRepo(@RequestBody RepoConfig config) {
        if (repoRepo.findByRepoName(config.getRepoName()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 존재하는 레포지토리명입니다: " + config.getRepoName()));
        }
        return ResponseEntity.ok(repoRepo.save(config));
    }

    // ── 레포 수정 ─────────────────────────────────────────
    @PutMapping("/repos/{id}")
    public ResponseEntity<?> updateRepo(@PathVariable Long id, @RequestBody RepoConfig config) {
        if (!repoRepo.existsById(id)) return ResponseEntity.notFound().build();
        config.setId(id);
        return ResponseEntity.ok(repoRepo.save(config));
    }

    // ── 레포 삭제 ─────────────────────────────────────────
    @DeleteMapping("/repos/{id}")
    public ResponseEntity<?> deleteRepo(@PathVariable Long id) {
        if (!repoRepo.existsById(id)) return ResponseEntity.notFound().build();
        repoRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "삭제 완료"));
    }

    // ── YAML 파일 임포트 (경로 미입력 시 기본값 사용) ────
    @PostMapping("/import-yaml")
    public ResponseEntity<?> importYaml(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        if (filePath == null || filePath.isBlank())
            filePath = yamlConfigService.getDefaultConfigPath();
        try {
            Map<String, Object> result = yamlConfigService.importFromYaml(filePath.trim());
            return ResponseEntity.ok(result);
        } catch (java.io.FileNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일을 찾을 수 없습니다: " + filePath));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "임포트 실패: " + e.getMessage()));
        }
    }
}
