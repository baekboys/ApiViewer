package com.baek.viewer.repository;

import com.baek.viewer.model.RepoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepoConfigRepository extends JpaRepository<RepoConfig, Long> {
    List<RepoConfig> findAllByOrderByRepoNameAsc();
    Optional<RepoConfig> findByRepoName(String repoName);
}
