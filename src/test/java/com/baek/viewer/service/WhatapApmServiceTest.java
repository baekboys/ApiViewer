package com.baek.viewer.service;

import com.baek.viewer.model.RepoConfig;
import com.baek.viewer.repository.ApmCallDataRepository;
import com.baek.viewer.repository.GlobalConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WhatapApmService 단위테스트.
 * 실제 HTTP 호출은 망분리 환경상 테스트 불가 — URL 미설정 예외 분기만 테스트.
 */
@ExtendWith(MockitoExtension.class)
class WhatapApmServiceTest {

    @Mock
    private ApmCallDataRepository apmRepo;

    @Mock
    private GlobalConfigRepository globalConfigRepo;

    @InjectMocks
    private WhatapApmService service;

    @Test
    @DisplayName("collect — WhatapUrl 이 null 이면 IllegalStateException")
    void collect_nullUrl_throws() {
        RepoConfig repo = new RepoConfig();
        repo.setRepoName("testRepo");

        assertThatThrownBy(() -> service.collect(repo, LocalDate.now().minusDays(1), LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATAP URL");
    }

    @Test
    @DisplayName("collect — WhatapUrl 이 공백이면 IllegalStateException")
    void collect_blankUrl_throws() {
        RepoConfig repo = new RepoConfig();
        repo.setRepoName("testRepo");
        repo.setWhatapUrl("   ");

        assertThatThrownBy(() -> service.collect(repo, LocalDate.now().minusDays(1), LocalDate.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("collect(logCallback) — null URL 시에도 동일 예외")
    void collect_withCallback_nullUrl_throws() {
        RepoConfig repo = new RepoConfig();
        repo.setRepoName("testRepo");

        assertThatThrownBy(() ->
                service.collect(repo, LocalDate.now().minusDays(1), LocalDate.now(), (level, msg) -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MOCK");
    }
}
