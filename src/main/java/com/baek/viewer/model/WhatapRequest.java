package com.baek.viewer.model;

public class WhatapRequest {
    private String whatapUrl;
    private int pcode = 8;
    private String cookie;
    private String startDate; // YYYYMMDD 또는 YYYY-MM-DD
    private String endDate;
    private String filters;  // 쉼표 구분 (빈 문자열 = 전체)
    private String okinds;   // 쉼표 구분 (빈 문자열 = 전체)

    public String getWhatapUrl() { return whatapUrl; }
    public void setWhatapUrl(String whatapUrl) { this.whatapUrl = whatapUrl; }

    public int getPcode() { return pcode; }
    public void setPcode(int pcode) { this.pcode = pcode; }

    public String getCookie() { return cookie; }
    public void setCookie(String cookie) { this.cookie = cookie; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }

    public String getOkinds() { return okinds; }
    public void setOkinds(String okinds) { this.okinds = okinds; }
}
