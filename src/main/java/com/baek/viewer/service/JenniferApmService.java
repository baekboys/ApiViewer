package com.baek.viewer.service;

import com.baek.viewer.model.ApmCallData;
import com.baek.viewer.model.ApiRecord;
import com.baek.viewer.model.GlobalConfig;
import com.baek.viewer.model.RepoConfig;
import com.baek.viewer.repository.ApmCallDataRepository;
import com.baek.viewer.repository.ApiRecordRepository;
import com.baek.viewer.repository.GlobalConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Jennifer APM 연동 서비스. (최대 30일 제약)
 *
 * jenniferMockEnabled=true : Jennifer 응답 스키마와 동일한 형태의 Mock 데이터 생성
 * jenniferMockEnabled=false: 실제 Jennifer API 호출 (URL 필수)
 *
 * 요청 형식 (GET):
 * {url}?domain_id={sid}&instance_id={oid1,oid2,...}&start_time={epochMs}&end_time={epochMs}
 * - start_time/end_time: 시 단위 epoch ms (분/초 = 0)
 *
 * 응답 형식:
 * { result:[{ name:"apiPath", calls:N, badResponses:N, failures:N }] }
 * errorCount = badResponses + failures
 */
@Service
public class JenniferApmService {

    private static final Logger log = LoggerFactory.getLogger(JenniferApmService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApmCallDataRepository apmRepo;
    private final ApiRecordRepository apiRecordRepo;
    private final GlobalConfigRepository globalConfigRepo;

    public JenniferApmService(ApmCallDataRepository apmRepo, ApiRecordRepository apiRecordRepo,
                               GlobalConfigRepository globalConfigRepo) {
        this.apmRepo = apmRepo;
        this.apiRecordRepo = apiRecordRepo;
        this.globalConfigRepo = globalConfigRepo;
    }

    /**
     * 날짜 범위를 일별로 수집하여 ApmCallData 저장.
     * jenniferMockEnabled=true 이면 Mock, false 이면 실제 API (URL 필수).
     * 트랜잭션은 호출자(MockApmService.generateMockDataByRange)가 관리.
     */
    public Map<String, Object> collect(RepoConfig repo, LocalDate from, LocalDate to) {
        GlobalConfig gc = globalConfigRepo.findById(1L).orElse(new GlobalConfig());
        boolean useMock = gc.isJenniferMockEnabled();

        if (!useMock && (repo.getJenniferUrl() == null || repo.getJenniferUrl().isBlank())) {
            throw new IllegalStateException(
                    "Jennifer URL이 설정되지 않았고 jenniferMockEnabled=false 입니다. " +
                    "repos-config.yml의 global.jenniferMockEnabled를 true로 설정하거나 Jennifer URL을 입력하세요.");
        }

        List<ApiRecord> records = apiRecordRepo.findByRepositoryName(repo.getRepoName());
        if (records.isEmpty()) {
            return Map.of("generated", 0, "message", "해당 레포에 분석된 API가 없습니다.");
        }

        String instanceId = buildInstanceId(repo.getJenniferOids());

        int generated = 0;
        List<ApmCallData> batch = new ArrayList<>();
        LocalDate cursor = from;

        while (!cursor.isAfter(to)) {
            // 자정은 분/초 = 0이므로 시 단위 조건 충족
            long startTime = cursor.atStartOfDay(KST).toInstant().toEpochMilli();
            long endTime   = cursor.plusDays(1).atStartOfDay(KST).toInstant().toEpochMilli();

            try {
                Map<String, long[]> dayData = useMock
                        ? buildMockDayData(records)
                        : fetchRealDay(repo, instanceId, startTime, endTime);

                for (ApiRecord rec : records) {
                    long[] counts = dayData.getOrDefault(rec.getApiPath(), new long[]{0L, 0L});
                    batch.add(buildEntry(repo.getRepoName(), rec, cursor, counts[0], counts[1]));
                    generated++;
                    if (batch.size() >= 1000) { apmRepo.saveAll(batch); batch.clear(); }
                }
                log.debug("[JENNIFER{}] {} 수집: {}건", useMock ? "-MOCK" : "", cursor, dayData.size());
            } catch (Exception e) {
                log.warn("[JENNIFER{}] {} 수집 실패 (스킵): {}", useMock ? "-MOCK" : "", cursor, e.getMessage());
            }
            cursor = cursor.plusDays(1);
        }

        if (!batch.isEmpty()) apmRepo.saveAll(batch);
        log.info("[JENNIFER{}] 수집 완료: repo={}, {}건 ({}~{})",
                useMock ? "-MOCK" : "", repo.getRepoName(), generated, from, to);
        return Map.of("generated", generated, "from", from.toString(), "to", to.toString(),
                "source", "JENNIFER", "mock", useMock);
    }

    /**
     * Jennifer 응답 스키마와 동일한 형태로 Mock 데이터 생성 후 동일 파서로 처리.
     *
     * 생성 스키마:
     * { result:[{ name:"apiPath", calls:N, badResponses:N, failures:N }] }
     */
    private Map<String, long[]> buildMockDayData(List<ApiRecord> records) throws Exception {
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (ApiRecord rec : records) {
            long calls = "차단완료".equals(rec.getStatus())
                    ? 0L : ThreadLocalRandom.current().nextLong(0, 150);
            long badResponses = calls > 0 ? ThreadLocalRandom.current().nextLong(0, Math.max(1, calls / 20)) : 0L;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", rec.getApiPath());
            r.put("calls", calls);
            r.put("badResponses", badResponses);
            r.put("failures", 0);
            resultList.add(r);
        }
        Map<String, Object> mockResponse = Map.of("result", resultList);

        return parseResponse(objectMapper.writeValueAsString(mockResponse));
    }

    private Map<String, long[]> fetchRealDay(RepoConfig repo, String instanceId,
                                              long startTime, long endTime) throws Exception {
        StringBuilder url = new StringBuilder(repo.getJenniferUrl())
                .append("?domain_id=").append(repo.getJenniferSid())
                .append("&start_time=").append(startTime)
                .append("&end_time=").append(endTime);
        if (!instanceId.isBlank()) {
            url.append("&instance_id=").append(URLEncoder.encode(instanceId, StandardCharsets.UTF_8));
        }
        if (repo.getJenniferFilter() != null && !repo.getJenniferFilter().isBlank()) {
            url.append("&filter=").append(URLEncoder.encode(repo.getJenniferFilter(), StandardCharsets.UTF_8));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (repo.getJenniferBearerToken() != null && !repo.getJenniferBearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + repo.getJenniferBearerToken());
        }

        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " — " + resp.body());
        }
        return parseResponse(resp.body());
    }

    /** Jennifer 응답 JSON 파싱 (실제/Mock 공통) */
    private Map<String, long[]> parseResponse(String responseBody) throws Exception {
        Map<String, long[]> result = new HashMap<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode resultNode = root.path("result");
        if (resultNode.isArray()) {
            for (JsonNode r : resultNode) {
                String name = r.path("name").asText(null);
                if (name == null || name.isBlank()) continue;
                long calls  = r.path("calls").asLong(0);
                long errors = r.path("badResponses").asLong(0) + r.path("failures").asLong(0);
                result.put(name, new long[]{calls, errors});
            }
        }
        return result;
    }

    /** jennifer_oids JSON ([{"oid":10021,"shortName":"..."},...]) → "10021,10022" */
    private String buildInstanceId(String jenniferOids) {
        if (jenniferOids == null || jenniferOids.isBlank()) return "";
        try {
            StringJoiner sj = new StringJoiner(",");
            for (JsonNode node : objectMapper.readTree(jenniferOids)) {
                String oid = node.path("oid").asText(null);
                if (oid != null) sj.add(oid);
            }
            return sj.toString();
        } catch (Exception e) {
            log.warn("[JENNIFER] OID 파싱 실패: {}", e.getMessage());
            return "";
        }
    }

    private ApmCallData buildEntry(String repoName, ApiRecord rec, LocalDate date,
                                    long callCount, long errorCount) {
        ApmCallData d = new ApmCallData();
        d.setRepositoryName(repoName);
        d.setApiPath(rec.getApiPath());
        d.setCallDate(date);
        d.setCallCount(callCount);
        d.setErrorCount(errorCount);
        d.setClassName(rec.getControllerName());
        d.setSource("JENNIFER");
        return d;
    }
}
