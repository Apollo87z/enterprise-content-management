package com.ecm.enterprisecontentmanagementbackend.repository;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository
        extends JpaRepository<DocumentVersionEntity, Long> {

    List<DocumentVersionEntity>
    findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersionEntity>
    findByDocumentIdAndVersionNumber(
            Long documentId,
            Integer versionNumber
    );

    Optional<DocumentVersionEntity>
    findTopByDocumentIdOrderByVersionNumberDesc(Long documentId);
}
