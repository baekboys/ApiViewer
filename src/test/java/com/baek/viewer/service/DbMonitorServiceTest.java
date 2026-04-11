package com.baek.viewer.service;

import com.baek.viewer.model.DbSizeHistory;
import com.baek.viewer.repository.ApiRecordRepository;
import com.baek.viewer.repository.ApmCallDataRepository;
import com.baek.viewer.repository.DbSizeHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DbMonitorService 단위테스트.
 * DataSource + 3개 Repository mock.
 * H2 분기 기반으로 파일 크기 조회는 0 반환 경로 사용 (실제 파일 의존 제거).
 */
@ExtendWith(MockitoExtension.class)
class DbMonitorServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private DbSizeHistoryRepository historyRepo;

    @Mock
    private ApiRecordRepository apiRecordRepo;

    @Mock
    private ApmCallDataRepository apmRepo;

    @InjectMocks
    private DbMonitorService service;

    @BeforeEach
    void setUp() {
        // H2 URL — 파일 없음 (size=0)
        ReflectionTestUtils.setField(service, "datasourceUrl",
                "jdbc:h2:file:./nonexistent-test-db;MODE=LEGACY");
    }

    @Test
    @DisplayName("getCurrent — H2 모드에서 dbType=H2 반환, 필수 필드 포함")
    void getCurrent_h2_returnsBasicFields() {
        when(apiRecordRepo.count()).thenReturn(100L);
        when(apmRepo.count()).thenReturn(500L);
        when(historyRepo.findBySnapshotDate(any(LocalDate.class))).thenReturn(Optional.empty());

        Map<String, Object> result = service.getCurrent();

        assertThat(result).containsEntry("dbType", "H2");
        assertThat(result).containsEntry("apiRecordCount", 100L);
        assertThat(result).containsEntry("apmCallDataCount", 500L);
        assertThat(result).containsKeys("dbFilePath", "dbSizeBytes", "diskTotalBytes", "osName", "javaVersion", "timestamp");
    }

    @Test
    @DisplayName("getCurrent — 어제 스냅샷이 있으면 todayGrowth 가 계산됨")
    void getCurrent_withYesterdaySnapshot_computesGrowth() {
        when(apiRecordRepo.count()).thenReturn(100L);
        when(apmRepo.count()).thenReturn(550L);
        DbSizeHistory yesterday = new DbSizeHistory();
        yesterday.setDbSizeBytes(1000L);
        yesterday.setApmCallDataCount(500L);
        when(historyRepo.findBySnapshotDate(any(LocalDate.class))).thenReturn(Optional.of(yesterday));

        Map<String, Object> result = service.getCurrent();

        // dbSize=0 이지만 어제=1000 이므로 growth = 0 - 1000 = -1000
        assertThat(result).containsEntry("todayGrowthBytes", -1000L);
        assertThat(result).containsEntry("todayGrowthApm", 50L);
    }

    @Test
    @DisplayName("takeSnapshot — 기존 스냅샷이 없으면 새로 save")
    void takeSnapshot_newRecord_saves() {
        when(apiRecordRepo.count()).thenReturn(10L);
        when(apmRepo.count()).thenReturn(20L);
        when(historyRepo.findBySnapshotDate(any(LocalDate.class))).thenReturn(Optional.empty());
        when(historyRepo.save(any(DbSizeHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        DbSizeHistory snap = service.takeSnapshot();

        assertThat(snap).isNotNull();
        assertThat(snap.getSnapshotDate()).isEqualTo(LocalDate.now());
        assertThat(snap.getApiRecordCount()).isEqualTo(10L);
        assertThat(snap.getApmCallDataCount()).isEqualTo(20L);
        verify(historyRepo, times(1)).save(any(DbSizeHistory.class));
    }

    @Test
    @DisplayName("takeSnapshot — 기존 스냅샷이 있으면 업데이트")
    void takeSnapshot_existing_updates() {
        DbSizeHistory existing = new DbSizeHistory();
        existing.setSnapshotDate(LocalDate.now());
        existing.setDbSizeBytes(999L);
        when(apiRecordRepo.count()).thenReturn(1L);
        when(apmRepo.count()).thenReturn(2L);
        when(historyRepo.findBySnapshotDate(any(LocalDate.class))).thenReturn(Optional.of(existing));
        when(historyRepo.save(any(DbSizeHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        DbSizeHistory snap = service.takeSnapshot();

        assertThat(snap).isSameAs(existing);
        assertThat(existing.getApiRecordCount()).isEqualTo(1L);
        assertThat(existing.getApmCallDataCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getHistory — 기록이 없으면 빈 리스트")
    void getHistory_empty() {
        when(historyRepo.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any(LocalDate.class)))
                .thenReturn(List.of());

        List<Map<String, Object>> list = service.getHistory(30);

        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("getHistory — 첫 행은 delta 가 0, 이후 행은 직전 대비 diff")
    void getHistory_computesDelta() {
        DbSizeHistory s1 = new DbSizeHistory();
        s1.setSnapshotDate(LocalDate.now().minusDays(2));
        s1.setDbSizeBytes(100L);
        s1.setApmCallDataCount(10L);
        DbSizeHistory s2 = new DbSizeHistory();
        s2.setSnapshotDate(LocalDate.now().minusDays(1));
        s2.setDbSizeBytes(150L);
        s2.setApmCallDataCount(25L);
        when(historyRepo.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any(LocalDate.class)))
                .thenReturn(List.of(s1, s2));

        List<Map<String, Object>> list = service.getHistory(7);

        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("deltaBytes", 0L);
        assertThat(list.get(0)).containsEntry("deltaApm", 0L);
        assertThat(list.get(1)).containsEntry("deltaBytes", 50L);
        assertThat(list.get(1)).containsEntry("deltaApm", 15L);
    }
}
