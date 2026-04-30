package com.baek.viewer.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_record_snapshot", indexes = {
        @Index(name = "idx_snapshot_at", columnList = "snapshot_at"),
        @Index(name = "idx_snapshot_type", columnList = "snapshot_type")
})
public class ApiRecordSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @Column(name = "snapshot_type", length = 30)
    private String snapshotType;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "created_ip", length = 50)
    private String createdIp;

    @Column(name = "source_repo", length = 100)
    private String sourceRepo;

    @Column(name = "record_count")
    private Long recordCount;

    public Long getId() { return id; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public String getSnapshotType() { return snapshotType; }
    public void setSnapshotType(String snapshotType) { this.snapshotType = snapshotType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getCreatedIp() { return createdIp; }
    public void setCreatedIp(String createdIp) { this.createdIp = createdIp; }
    public String getSourceRepo() { return sourceRepo; }
    public void setSourceRepo(String sourceRepo) { this.sourceRepo = sourceRepo; }
    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }
}

