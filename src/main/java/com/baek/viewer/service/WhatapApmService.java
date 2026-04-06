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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Whatap APM 연동 서비스.
 *
 * whatapMockEnabled=true : Whatap 응답 스키마와 동일한 형태의 Mock 데이터 생성
 * whatapMockEnabled=false: 실제 Whatap API 호출 (URL 필수)
 *
 * 요청 형식 (POST JSON):
 * { type:"stat", path:"ap", pcode:N, stime:epochMs, etime:epochMs,
 *   params:{ stime:"", etime:"", ptotal:100, skip:0, psize:N,
 *            okinds:[...], order:"countTotal", type:"service" } }
 *
 * 응답 형식:
 * { stime:epochMs, etime:epochMs, records:[{ service:"apiPath", count:N, error:N }] }
 */
@Service
public class WhatapApmService {

    private static final Logger log = LoggerFactory.getLogger(WhatapApmService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApmCallDataRepository apmRepo;
    private final ApiRecordRepository apiRecordRepo;
    private final GlobalConfigRepository globalConfigRepo;

    public WhatapApmService(ApmCallDataRepository apmRepo, ApiRecordRepository apiRecordRepo,
                             GlobalConfigRepository globalConfigRepo) {
        this.apmRepo = apmRepo;
        this.apiRecordRepo = apiRecordRepo;
        this.globalConfigRepo = globalConfigRepo;
    }

    /**
     * 날짜 범위를 일별로 수집하여 ApmCallData 저장.
     * whatapMockEnabled=true 이면 Mock, false 이면 실제 API (URL 필수).
     * 트랜잭션은 호출자(MockApmService.generateMockDataByRange)가 관리.
     */
    public Map<String, Object> collect(RepoConfig repo, LocalDate from, LocalDate to) {
        GlobalConfig gc = globalConfigRepo.findById(1L).orElse(new GlobalConfig());
        boolean useMock = gc.isWhatapMockEnabled();

        if (!useMock && (repo.getWhatapUrl() == null || repo.getWhatapUrl().isBlank())) {
            throw new IllegalStateException(
                    "WHATAP URL이 설정되지 않았고 whatapMockEnabled=false 입니다. " +
                    "repos-config.yml의 global.whatapMockEnabled를 true로 설정하거나 Whatap URL을 입력하세요.");
        }

        List<ApiRecord> records = apiRecordRepo.findByRepositoryName(repo.getRepoName());
        if (records.isEmpty()) {
            return Map.of("generated", 0, "message", "해당 레포에 분석된 API가 없습니다.");
        }

        List<Integer> okinds = parseOkinds(repo.getWhatapOkinds());
        int psize = Math.max(records.size(), 100);

        int generated = 0;
        List<ApmCallData> batch = new ArrayList<>();
        LocalDate cursor = from;

        while (!cursor.isAfter(to)) {
            long stimeMs = cursor.atStartOfDay(KST).toInstant().toEpochMilli();
            long etimeMs = cursor.plusDays(1).atStartOfDay(KST).toInstant().toEpochMilli() - 1;

            try {
                Map<String, long[]> dayData = useMock
                        ? buildMockDayData(records, stimeMs, etimeMs)
                        : fetchRealDay(repo, stimeMs, etimeMs, okinds, psize);

                for (ApiRecord rec : records) {
                    long[] counts = dayData.getOrDefault(rec.getApiPath(), new long[]{0L, 0L});
                    batch.add(buildEntry(repo.getRepoName(), rec, cursor, counts[0], counts[1]));
                    generated++;
                    if (batch.size() >= 1000) { apmRepo.saveAll(batch); batch.clear(); }
                }
                log.debug("[WHATAP{}] {} 수집: {}건", useMock ? "-MOCK" : "", cursor, dayData.size());
            } catch (Exception e) {
                log.warn("[WHATAP{}] {} 수집 실패 (스킵): {}", useMock ? "-MOCK" : "", cursor, e.getMessage());
            }
            cursor = cursor.plusDays(1);
        }

        if (!batch.isEmpty()) apmRepo.saveAll(batch);
        log.info("[WHATAP{}] 수집 완료: repo={}, {}건 ({}~{})",
                useMock ? "-MOCK" : "", repo.getRepoName(), generated, from, to);
        return Map.of("generated", generated, "from", from.toString(), "to", to.toString(),
                "source", "WHATAP", "mock", useMock);
    }

    /**
     * Whatap 응답 스키마와 동일한 형태로 Mock 데이터 생성 후 동일 파서로 처리.
     *
     * 생성 스키마:
     * { stime:N, etime:N, records:[{ service:"apiPath", count:N, error:N }] }
     */
    private Map<String, long[]> buildMockDayData(List<ApiRecord> records,
                                                   long stime, long etime) throws Exception {
        List<Map<String, Object>> recordsList = new ArrayList<>();
        for (ApiRecord rec : records) {
            long count = "차단완료".equals(rec.getStatus())
                    ? 0L : ThreadLocalRandom.current().nextLong(0, 150);
            long error = count > 0 ? ThreadLocalRandom.current().nextLong(0, Math.max(1, count / 20)) : 0L;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("service", rec.getApiPath());
            r.put("count", count);
            r.put("error", error);
            recordsList.add(r);
        }
        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.put("stime", stime);
        mockResponse.put("etime", etime);
        mockResponse.put("records", recordsList);

        return parseResponse(objectMapper.writeValueAsString(mockResponse));
    }

    private Map<String, long[]> fetchRealDay(RepoConfig repo, long stime, long etime,
                                              List<Integer> okinds, int psize) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stime", "");
        params.put("etime", "");
        params.put("ptotal", 100);
        params.put("skip", 0);
        params.put("psize", psize);
        params.put("okinds", okinds);
        params.put("order", "countTotal");
        params.put("type", "service");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "stat");
        body.put("path", "ap");
        body.put("pcode", repo.getWhatapPcode());
        body.put("stime", stime);
        body.put("etime", etime);
        body.put("params", params);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(repo.getWhatapUrl()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (repo.getWhatapCookie() != null && !repo.getWhatapCookie().isBlank()) {
            builder.header("Cookie", repo.getWhatapCookie());
        }

        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " — " + resp.body());
        }
        return parseResponse(resp.body());
    }

    /** Whatap 응답 JSON 파싱 (실제/Mock 공통) */
    private Map<String, long[]> parseResponse(String responseBody) throws Exception {
        Map<String, long[]> result = new HashMap<>();
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode recordsNode = root.path("records");
        if (recordsNode.isArray()) {
            for (JsonNode r : recordsNode) {
                String service = r.path("service").asText(null);
                if (service == null || service.isBlank()) continue;
                result.put(service, new long[]{r.path("count").asLong(0), r.path("error").asLong(0)});
            }
        }
        return result;
    }

    /** whatapOkinds 컬럼 값(콤마구분 또는 JSON 배열) → Integer 리스트 */
    private List<Integer> parseOkinds(String okindsStr) {
        List<Integer> result = new ArrayList<>();
        if (okindsStr == null || okindsStr.isBlank()) return result;
        String s = okindsStr.trim();
        if (s.startsWith("[")) {
            try {
                for (JsonNode n : objectMapper.readTree(s)) result.add(n.asInt());
                return result;
            } catch (Exception ignored) {}
        }
        for (String tok : s.split(",")) {
            try { result.add(Integer.parseInt(tok.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
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
        d.setSource("WHATAP");
        return d;
    }
}
