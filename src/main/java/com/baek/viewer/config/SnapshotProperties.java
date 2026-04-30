package com.baek.viewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api.viewer.snapshot")
public class SnapshotProperties {
    /** 기본 보관일수 (GlobalConfig.snapshotRetentionDays 가 null이면 이 값을 사용) */
    private int retentionDays = 365;

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
}

