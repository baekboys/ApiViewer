package com.baek.viewer.model;

import java.util.Map;

public class WhatapResult {
    private Map<String, Long> stats;  // API 경로 → 총 호출건수
    private int segmentCount;
    private int apiCount;
    private String message;

    public WhatapResult(Map<String, Long> stats, int segmentCount, int apiCount, String message) {
        this.stats = stats;
        this.segmentCount = segmentCount;
        this.apiCount = apiCount;
        this.message = message;
    }

    public Map<String, Long> getStats() { return stats; }
    public int getSegmentCount() { return segmentCount; }
    public int getApiCount() { return apiCount; }
    public String getMessage() { return message; }
}
