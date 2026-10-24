package com.ecm.enterprisecontentmanagementbackend.repository;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAuditRepository
        extends JpaRepository<DocumentAuditEntity, Long> {

    Page<DocumentAuditEntity> findByDocumentId(
            Long documentId,
            Pageable pageable
    );
}