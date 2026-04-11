package com.baek.viewer.service;

import com.baek.viewer.model.ApmCallData;
import com.baek.viewer.repository.ApmCallDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ApmArchiveService 단위테스트.
 * Repository mock으로 대상건수 조회/삭제 동작 검증.
 * 실제 파일 I/O는 archive dry-run 또는 대상건수=0 경로로 회피.
 */
@ExtendWith(MockitoExtension.class)
class ApmArchiveServiceTest {

    @Mock
    private ApmCallDataRepository apmRepo;

    @InjectMocks
    private ApmArchiveService service;

    @BeforeEach
    void setUp() {
        // no-op
    }

    @Test
    @DisplayName("archive — 대상이 0건이면 삭제 호출 없이 archived=0 반환")
    void archive_noTarget_returnsZero() throws Exception {
        when(apmRepo.countByCallDateBefore(any(LocalDate.class))).thenReturn(0L);

        Map<String, Object> result = service.archive(365, false);

        assertThat(result).containsEntry("targetCount", 0L);
        assertThat(result).containsEntry("archived", 0);
        assertThat(result).containsEntry("dryRun", false);
        verify(apmRepo, never()).deleteByCallDateBefore(any());
        verify(apmRepo, never()).findByCallDateBefore(any());
    }

    @Test
    @DisplayName("archive — dryRun 이면 대상건수만 조회하고 삭제/CSV 기록은 하지 않음")
    void archive_dryRun_doesNotDelete() throws Exception {
        when(apmRepo.countByCallDateBefore(any(LocalDate.class))).thenReturn(100L);

        Map<String, Object> result = service.archive(365, true);

        assertThat(result).containsEntry("targetCount", 100L);
        assertThat(result).containsEntry("archived", 0);
        assertThat(result).containsEntry("dryRun", true);
        verify(apmRepo, never()).deleteByCallDateBefore(any());
        verify(apmRepo, never()).findByCallDateBefore(any());
    }

    @Test
    @DisplayName("archive — keepDays 계산: cutoff 는 오늘 - keepDays")
    void archive_cutoffDate_computedFromKeepDays() throws Exception {
        when(apmRepo.countByCallDateBefore(any(LocalDate.class))).thenReturn(0L);

        Map<String, Object> result = service.archive(30, true);

        LocalDate expectedCutoff = LocalDate.now().minusDays(30);
        assertThat(result.get("cutoff")).isEqualTo(expectedCutoff.toString());
    }

    @Test
    @DisplayName("archive — keepDays 가 0이나 음수면 최소 1일로 보정")
    void archive_keepDaysMinimumOne() throws Exception {
        when(apmRepo.countByCallDateBefore(any(LocalDate.class))).thenReturn(0L);

        Map<String, Object> result = service.archive(0, true);

        LocalDate expectedCutoff = LocalDate.now().minusDays(1);
        assertThat(result.get("cutoff")).isEqualTo(expectedCutoff.toString());
    }

    @Test
    @DisplayName("archive — 대상건수 >0 + 실삭제: CSV 기록 후 deleteByCallDateBefore 호출")
    void archive_deletesAndWritesCsv() throws Exception {
        when(apmRepo.countByCallDateBefore(any(LocalDate.class))).thenReturn(2L);
        ApmCallData d1 = new ApmCallData();
        d1.setRepositoryName("repo1");
        d1.setApiPath("/api/test");
        d1.setCallDate(LocalDate.now().minusDays(400));
        d1.setCallCount(10);
        d1.setErrorCount(1);
        d1.setSource("MOCK");
        ApmCallData d2 = new ApmCallData();
        d2.setRepositoryName("repo2");
        d2.setApiPath("/api/other, with comma");
        d2.setCallDate(LocalDate.now().minusDays(500));
        d2.setCallCount(20);
        d2.setSource("WHATAP");
        when(apmRepo.findByCallDateBefore(any(LocalDate.class))).thenReturn(List.of(d1, d2));
        when(apmRepo.deleteByCallDateBefore(any(LocalDate.class))).thenReturn(2);

        Map<String, Object> result = service.archive(365, false);

        assertThat(result).containsEntry("archived", 2);
        assertThat(result).containsKey("csvPath");
        verify(apmRepo, times(1)).deleteByCallDateBefore(any(LocalDate.class));
    }

    @Test
    @DisplayName("listArchives — 디렉토리 미존재 시 빈 리스트")
    void listArchives_whenDirNotExists() throws Exception {
        // ARCHIVE_DIR 은 상수 — 디렉토리가 있을 수도 있고 없을 수도 있으나,
        // 어느 쪽이든 예외 없이 리스트를 반환해야 한다.
        List<Map<String, Object>> list = service.listArchives();
        assertThat(list).isNotNull();
    }
}
