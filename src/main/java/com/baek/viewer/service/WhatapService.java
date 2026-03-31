package com.baek.viewer.service;

import com.baek.viewer.model.WhatapRequest;
import com.baek.viewer.model.WhatapResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class WhatapService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ──────────────────────────────────────────────────────────────────
    // 퍼블릭 진입점
    // ──────────────────────────────────────────────────────────────────

    public WhatapResult fetchStats(WhatapRequest req) {
        validate(req);

        List<FetchSegment> segments = generateSegments(req.getStartDate(), req.getEndDate());
        Map<String, long[]> rawMap = new ConcurrentHashMap<>();
        List<String> filters = parseFilters(req.getFilters());

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            final int idx = i;
            final FetchSegment seg = segments.get(i);
            for (String filter : filters) {
                futures.add(CompletableFuture.runAsync(
                        () -> fetchSegment(req, seg, idx, filter, rawMap, segments.size()),
                        executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // long[] 배열의 합계를 총 호출건수로 집계
        Map<String, Long> totals = new HashMap<>();
        for (Map.Entry<String, long[]> e : rawMap.entrySet()) {
            long sum = 0;
            for (long v : e.getValue()) sum += v;
            totals.put(e.getKey(), sum);
        }

        String msg = String.format("%d개 구간 완료 · 수집 API %d건", segments.size(), totals.size());
        return new WhatapResult(totals, segments.size(), totals.size(), msg);
    }

    // ──────────────────────────────────────────────────────────────────
    // 시간 구간 생성 (10일 단위 - WhatapApiCounter 동일 로직)
    // ──────────────────────────────────────────────────────────────────

    private List<FetchSegment> generateSegments(String startStr, String endStr) {
        LocalDate startLimit = parseDate(startStr);
        LocalDate endLimit   = parseDate(endStr);
        List<FetchSegment> list = new ArrayList<>();

        LocalDate cur = startLimit.withDayOfMonth(1);
        while (!cur.isAfter(endLimit)) {
            String mKey = cur.format(DateTimeFormatter.ofPattern("yy.MM"));
            addSegment(list, cur.withDayOfMonth(1),  cur.withDayOfMonth(10), startLimit, endLimit, mKey);
            addSegment(list, cur.withDayOfMonth(11), cur.withDayOfMonth(20), startLimit, endLimit, mKey);
            addSegment(list, cur.withDayOfMonth(21), cur.withDayOfMonth(cur.lengthOfMonth()), startLimit, endLimit, mKey);
            cur = cur.plusMonths(1);
        }
        return list;
    }

    private void addSegment(List<FetchSegment> list,
                             LocalDate s, LocalDate e,
                             LocalDate limitS, LocalDate limitE, String monthKey) {
        LocalDate actualS = s.isBefore(limitS) ? limitS : s;
        LocalDate actualE = e.isAfter(limitE)  ? limitE : e;
        if (!actualS.isAfter(actualE)) {
            FetchSegment seg = new FetchSegment();
            seg.label    = actualS.format(DateTimeFormatter.ISO_LOCAL_DATE)
                         + "~" + actualE.getDayOfMonth();
            seg.stime    = actualS.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            seg.etime    = actualE.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            seg.monthKey = monthKey;
            list.add(seg);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // 구간별 HTTP 요청 (WhatapApiCounter.requestWithDetailedFetch 동일 로직)
    // ──────────────────────────────────────────────────────────────────

    private void fetchSegment(WhatapRequest req, FetchSegment seg, int segIdx,
                               String filter, Map<String, long[]> statsMap, int totalSegments) {
        try {
            String payload = buildPayload(req, seg.stime, seg.etime, filter);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(req.getWhatapUrl()))
                    .header("Content-Type", "application/json")
                    .header("Cookie", req.getCookie())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode records = MAPPER.readTree(response.body()).path("records");
                if (records.isArray()) {
                    for (JsonNode n : records) {
                        String svc  = n.path("service").asText();
                        long   cnt  = n.path("count").asLong();
                        long[] arr  = statsMap.computeIfAbsent(svc, k -> new long[totalSegments + 10]);
                        synchronized (arr) { arr[segIdx] += cnt; }
                    }
                }
            } else {
                System.err.printf("[Whatap] HTTP %d - 구간: %s%n", response.statusCode(), seg.label);
            }
        } catch (Exception e) {
            System.err.printf("[Whatap] 오류 - 구간: %s, %s%n", seg.label, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // 요청 페이로드 생성 (WhatapApiCounter 동일 포맷)
    // ──────────────────────────────────────────────────────────────────

    private String buildPayload(WhatapRequest req, long stime, long etime, String filter) {
        return String.format("""
            {
              "type": "stat",
              "path": "ap",
              "pcode": %d,
              "params": {
                "stime": %d,
                "etime": %d,
                "ptotal": 100,
                "skip": 0,
                "psize": 10000,
                "filter": { "service": "%s" },
                "okinds": [%s],
                "order": "countTotal",
                "type": "service"
              },
              "stime": %d,
              "etime": %d
            }""",
                req.getPcode(), stime, etime,
                filter,
                req.getOkinds() != null ? req.getOkinds() : "",
                stime, etime);
    }

    // ──────────────────────────────────────────────────────────────────
    // 유틸리티
    // ──────────────────────────────────────────────────────────────────

    /** YYYYMMDD 또는 YYYY-MM-DD 양쪽 포맷 허용 */
    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("날짜가 비어있습니다.");
        s = s.trim();
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(s); // ISO (YYYY-MM-DD)
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식 오류: " + s + " (YYYYMMDD 또는 YYYY-MM-DD)");
        }
    }

    private List<String> parseFilters(String raw) {
        if (raw == null || raw.isBlank()) return List.of("");
        List<String> result = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return result.isEmpty() ? List.of("") : result;
    }

    private void validate(WhatapRequest req) {
        if (req.getWhatapUrl() == null || req.getWhatapUrl().isBlank())
            throw new IllegalArgumentException("Whatap URL이 필요합니다.");
        if (req.getCookie() == null || req.getCookie().isBlank())
            throw new IllegalArgumentException("Whatap 쿠키가 필요합니다.");
        if (req.getStartDate() == null || req.getStartDate().isBlank())
            throw new IllegalArgumentException("시작일이 필요합니다.");
        if (req.getEndDate() == null || req.getEndDate().isBlank())
            throw new IllegalArgumentException("종료일이 필요합니다.");
    }

    // ──────────────────────────────────────────────────────────────────
    // 내부 구조체
    // ──────────────────────────────────────────────────────────────────

    private static class FetchSegment {
        String label;
        long   stime;
        long   etime;
        String monthKey;
    }
}
