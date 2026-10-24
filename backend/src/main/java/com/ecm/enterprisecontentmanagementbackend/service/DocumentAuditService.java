package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentAuditEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentAuditService {

    private final DocumentAuditRepository repository;

    public DocumentAuditService(DocumentAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            Long documentId,
            String action,
            String actor,
            String details
    ) {
        repository.save(
                new DocumentAuditEntity(
                        documentId,
                        action,
                        actor,
                        details
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<DocumentAuditEntity> getDocumentAuditLogs(
            Long documentId,
            Pageable pageable
    ) {
        return repository.findByDocumentId(documentId, pageable);
    }
}