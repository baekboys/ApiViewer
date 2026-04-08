package com.baek.viewer.service;

import com.baek.viewer.model.DbSizeHistory;
import com.baek.viewer.repository.ApiRecordRepository;
import com.baek.viewer.repository.ApmCallDataRepository;
import com.baek.viewer.repository.DbSizeHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * DB 사이즈 모니터링 서비스 (H2 / PostgreSQL 공통).
 * - H2: 파일 사이즈 기반
 * - PostgreSQL: pg_database_size() 쿼리 기반
 */
@Service
public class DbMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DbMonitorService.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private final DataSource dataSource;
    private final DbSizeHistoryRepository historyRepo;
    private final ApiRecordRepository apiRecordRepo;
    private final ApmCallDataRepository apmRepo;

    public DbMonitorService(DataSource dataSource, DbSizeHistoryRepository historyRepo,
                            ApiRecordRepository apiRecordRepo, ApmCallDataRepository apmRepo) {
        this.dataSource = dataSource;
        this.historyRepo = historyRepo;
        this.apiRecordRepo = apiRecordRepo;
        this.apmRepo = apmRepo;
    }

    private boolean isH2() {
        return datasourceUrl != null && datasourceUrl.contains("jdbc:h2:");
    }

    /** DB 사이즈 조회 (바이트) — H2: 파일크기, PostgreSQL: pg_database_size() */
    private long getDbSizeBytes() {
        if (isH2()) {
            return getH2FileSize();
        }
        // PostgreSQL
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT pg_database_size(current_database())")) {
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) {
            log.warn("[DB 모니터] PostgreSQL DB 사이즈 조회 실패: {}", e.getMessage());
        }
        return 0;
    }

    /** H2 파일 DB 사이즈 */
    private long getH2FileSize() {
        String prefix = "jdbc:h2:file:";
        int idx = datasourceUrl.indexOf(prefix);
        if (idx < 0) return 0;
        String raw = datasourceUrl.substring(idx + prefix.length());
        int semi = raw.indexOf(';');
        if (semi >= 0) raw = raw.substring(0, semi);
        Path dbFile = Paths.get(raw + ".mv.db").toAbsolutePath().normalize();
        try { if (Files.exists(dbFile)) return Files.size(dbFile); } catch (Exception e) {}
        return 0;
    }

    /** DB 파일 경로 (표시용) */
    private String getDbFilePath() {
        if (isH2()) {
            String prefix = "jdbc:h2:file:";
            int idx = datasourceUrl.indexOf(prefix);
            if (idx >= 0) {
                String raw = datasourceUrl.substring(idx + prefix.length());
                int semi = raw.indexOf(';');
                if (semi >= 0) raw = raw.substring(0, semi);
                return Paths.get(raw + ".mv.db").toAbsolutePath().normalize().toString();
            }
        }
        // PostgreSQL: URL 그대로 표시
        return datasourceUrl;
    }

    /** 현재 DB 사이즈 + 시스템 디스크 사용량 */
    public Map<String, Object> getCurrent() {
        long dbSize = getDbSizeBytes();
        String dbFilePath = getDbFilePath();

        // 디스크 공간 (H2는 DB 파일 디렉토리, PostgreSQL은 현재 작업 디렉토리 기준)
        File root = isH2() ? new File(dbFilePath).getParentFile() : new File(".");
        if (root == null || !root.exists()) root = new File(".");
        long total = root.getTotalSpace();
        long usable = root.getUsableSpace();
        long used = total - usable;

        long apiRecCount = apiRecordRepo.count();
        long apmCount = apmRepo.count();

        // 오늘 기준 증가량 (어제 스냅샷 대비)
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        long todayGrowthBytes = 0, todayGrowthApm = 0;
        var yesterdaySnap = historyRepo.findBySnapshotDate(yesterday);
        if (yesterdaySnap.isPresent()) {
            todayGrowthBytes = dbSize - yesterdaySnap.get().getDbSizeBytes();
            todayGrowthApm = apmCount - yesterdaySnap.get().getApmCallDataCount();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dbType", isH2() ? "H2" : "PostgreSQL");
        result.put("dbFilePath", dbFilePath);
        result.put("dbSizeBytes", dbSize);
        result.put("diskTotalBytes", total);
        result.put("diskUsedBytes", used);
        result.put("diskUsableBytes", usable);
        result.put("diskUsedPct", total > 0 ? Math.round((double) used / total * 1000) / 10.0 : 0);
        result.put("apiRecordCount", apiRecCount);
        result.put("apmCallDataCount", apmCount);
        result.put("todayGrowthBytes", todayGrowthBytes);
        result.put("todayGrowthApm", todayGrowthApm);
        result.put("osName", System.getProperty("os.name"));
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }

    /** 서버 기동 직후 오늘 스냅샷 보장 */
    @EventListener(ApplicationReadyEvent.class)
    public void snapshotOnStartup() {
        try {
            takeSnapshot();
            log.info("[DB 모니터] 기동 시 스냅샷 완료 ({})", isH2() ? "H2" : "PostgreSQL");
        } catch (Exception e) {
            log.warn("[DB 모니터] 기동 시 스냅샷 실패: {}", e.getMessage());
        }
    }

    /** 오늘 날짜 스냅샷 기록 (없으면 INSERT, 있으면 UPDATE) */
    @Transactional
    public DbSizeHistory takeSnapshot() {
        LocalDate today = LocalDate.now();
        long dbSize = getDbSizeBytes();
        long apiRecCount = apiRecordRepo.count();
        long apmCount = apmRepo.count();

        DbSizeHistory snap = historyRepo.findBySnapshotDate(today)
                .orElseGet(DbSizeHistory::new);
        snap.setSnapshotDate(today);
        snap.setDbSizeBytes(dbSize);
        snap.setApiRecordCount(apiRecCount);
        snap.setApmCallDataCount(apmCount);
        snap.setCreatedAt(LocalDateTime.now());
        historyRepo.save(snap);
        log.info("[DB 스냅샷] {} — DB {}MB, ApiRecord {}, ApmCallData {}",
                today, dbSize / 1024 / 1024, apiRecCount, apmCount);
        return snap;
    }

    /** 최근 N일 증가 추이 */
    public List<Map<String, Object>> getHistory(int days) {
        LocalDate from = LocalDate.now().minusDays(Math.max(1, days));
        List<DbSizeHistory> list = historyRepo.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(from);
        List<Map<String, Object>> out = new ArrayList<>();
        long prevSize = 0, prevApm = 0;
        boolean first = true;
        for (DbSizeHistory s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", s.getSnapshotDate().toString());
            m.put("dbSizeBytes", s.getDbSizeBytes());
            m.put("apiRecordCount", s.getApiRecordCount());
            m.put("apmCallDataCount", s.getApmCallDataCount());
            m.put("deltaBytes", first ? 0 : s.getDbSizeBytes() - prevSize);
            m.put("deltaApm", first ? 0 : s.getApmCallDataCount() - prevApm);
            out.add(m);
            prevSize = s.getDbSizeBytes();
            prevApm = s.getApmCallDataCount();
            first = false;
        }
        return out;
    }
}
