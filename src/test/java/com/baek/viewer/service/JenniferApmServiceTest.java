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
 * JenniferApmService 단위테스트.
 * 실제 HTTP 호출은 망분리 환경상 테스트 불가 — 파라미터 검증 및 URL 미설정 예외 분기만 테스트.
 */
@ExtendWith(MockitoExtension.class)
class JenniferApmServiceTest {

    @Mock
    private ApmCallDataRepository apmRepo;

    @Mock
    private GlobalConfigRepository globalConfigRepo;

    @InjectMocks
    private JenniferApmService service;

    @Test
    @DisplayName("collect — JenniferUrl 이 null 이면 IllegalStateException")
    void collect_nullUrl_throws() {
        RepoConfig repo = new RepoConfig();
        repo.setRepoName("testRepo");
        // jenniferUrl 미설정

        assertThatThrownBy(() -> service.collect(repo, LocalDate.now().minusDays(1), LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Jennifer URL");
    }

    @Test
    @DisplayName("collect — JenniferUrl 이 빈 문자열이면 IllegalStateException")
    void collect_blankUrl_throws() {
        RepoConfig repo = new RepoConfig();
        repo.setRepoName("testRepo");
        repo.setJenniferUrl("   ");

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
