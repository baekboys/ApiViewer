package com.baek.viewer.repository;

import com.baek.viewer.model.JiraUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JiraUserMappingRepository extends JpaRepository<JiraUserMapping, Long> {

    /** 팀명 + URLViewer명으로 매핑 조회 (복합키 기준) */
    Optional<JiraUserMapping> findByTeamNameAndUrlviewerName(String teamName, String urlviewerName);

    /** URLViewer명만으로 조회 (팀 정보 없을 때 폴백) */
    Optional<JiraUserMapping> findFirstByUrlviewerName(String urlviewerName);

    List<JiraUserMapping> findAll();
}
