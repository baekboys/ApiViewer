package com.baek.viewer.repository;

import com.baek.viewer.model.ApiRecordSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ApiRecordSnapshotRepository extends JpaRepository<ApiRecordSnapshot, Long> {
    Page<ApiRecordSnapshot> findBySnapshotAtBetweenOrderBySnapshotAtDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);
}

