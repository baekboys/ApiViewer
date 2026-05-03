package com.baek.viewer.repository;

import com.baek.viewer.model.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findBySlug(String slug);

    List<AiPromptTemplate> findAllByOrderBySlugAsc();
}
