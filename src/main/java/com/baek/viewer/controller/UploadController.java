package com.baek.viewer.controller;

import com.baek.viewer.model.ApiRecord;
import com.baek.viewer.repository.ApiRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 엑셀 업로드 — viewer.html에서 기존 레코드 편집 가능 필드 일괄 업데이트.
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private final ApiRecordRepository repository;

    public UploadController(ApiRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * viewer.html 엑셀 업로드 — 기존 레코드의 편집 가능 필드만 업데이트 (신규 생성 없음)
     * Body: { "rows": [ { "repositoryName":"...", "apiPath":"/foo", "httpMethod":"GET",
     *                     "status":"사용", "statusOverridden":"확정", "blockCriteria":"...",
     *                     "memo":"...", "reviewResult":"...", "reviewOpinion":"..." } ] }
     * 분석일시(lastAnalyzedAt)는 엑셀 값을 무시하고 업로드 시각으로 자동 설정.
     */
    @PostMapping("/excel-viewer")
    public ResponseEntity<?> uploadExcelViewer(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        if (rows == null || rows.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "업로드 데이터가 없습니다."));

        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";

        log.info("[viewer 엑셀 업로드] 건수={}, ip={}", rows.size(), ip);
        LocalDateTime now = LocalDateTime.now();
        int updated = 0, skipped = 0;
        List<Long> updatedIds = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String repoName  = str(row, "repositoryName");
            String apiPath   = str(row, "apiPath");
            String httpMethod = str(row, "httpMethod");
            if (repoName == null || repoName.isBlank() || apiPath == null || apiPath.isBlank()) { skipped++; continue; }
            if (httpMethod == null || httpMethod.isBlank()) httpMethod = "GET";

            Optional<ApiRecord> opt = repository.findByRepositoryNameAndApiPathAndHttpMethod(repoName, apiPath, httpMethod);
            if (opt.isEmpty()) { skipped++; continue; }

            ApiRecord r = opt.get();
            // 차단완료는 편집 불가
            if ("차단완료".equals(r.getStatus())) { skipped++; continue; }

            // 상태 — 변경 시 statusOverridden 자동 true
            String newStatus = str(row, "status");
            if (newStatus != null && !newStatus.isBlank() && !newStatus.equals(r.getStatus())) {
                r.setStatus(newStatus);
                r.setStatusOverridden(true);
            }
            // 상태확정 명시적 지정 (확정/미확정)
            String ovr = str(row, "statusOverridden");
            if ("확정".equals(ovr))   r.setStatusOverridden(true);
            else if ("미확정".equals(ovr)) r.setStatusOverridden(false);

            if (row.containsKey("blockCriteria")) r.setBlockCriteria(str(row, "blockCriteria"));
            if (row.containsKey("memo"))          r.setMemo(str(row, "memo"));
            if (row.containsKey("cboScheduledDate")) {
                String ds = str(row, "cboScheduledDate");
                r.setCboScheduledDate(ds == null || ds.isBlank() ? null : java.time.LocalDate.parse(ds));
            }
            if (row.containsKey("deployScheduledDate")) {
                String ds = str(row, "deployScheduledDate");
                r.setDeployScheduledDate(ds == null || ds.isBlank() ? null : java.time.LocalDate.parse(ds));
            }
            if (row.containsKey("deployCsr")) r.setDeployCsr(str(row, "deployCsr"));
            String rv = str(row, "reviewResult");
            if (row.containsKey("reviewResult"))  r.setReviewResult(rv == null || rv.isBlank() ? null : rv);
            if (row.containsKey("reviewOpinion")) r.setReviewOpinion(str(row, "reviewOpinion"));

            // 분석일시는 업로드 시각으로 고정
            r.setLastAnalyzedAt(now);
            r.setModifiedAt(now);
            r.setModifiedIp(ip);

            repository.save(r);
            updatedIds.add(r.getId());
            updated++;
        }

        log.info("[viewer 엑셀 업로드 완료] 업데이트={}, 스킵={}", updated, skipped);
        return ResponseEntity.ok(Map.of("updated", updated, "skipped", skipped, "total", rows.size(), "updatedIds", updatedIds));
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }
}
