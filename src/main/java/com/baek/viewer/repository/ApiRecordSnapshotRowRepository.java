package com.baek.viewer.repository;

import com.baek.viewer.model.ApiRecordSnapshotRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiRecordSnapshotRowRepository extends JpaRepository<ApiRecordSnapshotRow, Long> {

    @Query("""
            SELECT r FROM ApiRecordSnapshotRow r
            WHERE r.snapshotId = :snapshotId
              AND (:repo IS NULL OR r.repositoryName = :repo)
              AND (:status IS NULL OR r.status = :status)
              AND (:q IS NULL OR lower(r.apiPath) LIKE concat('%', lower(:q), '%'))
            """)
    Page<ApiRecordSnapshotRow> pageByFilters(@Param("snapshotId") Long snapshotId,
                                            @Param("repo") String repo,
                                            @Param("status") String status,
                                            @Param("q") String q,
                                            Pageable pageable);
}

