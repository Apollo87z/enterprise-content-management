package com.ecm.enterprisecontentmanagementbackend.dto;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentAuditEntity;
import java.time.OffsetDateTime;

public record DocumentAuditResponse(
        Long id,
        Long documentId,
        String action,
        String actor,
        String details,
        OffsetDateTime createdAt
) {

    public static DocumentAuditResponse from(DocumentAuditEntity entity) {
        return new DocumentAuditResponse(
                entity.getId(),
                entity.getDocumentId(),
                entity.getAction(),
                entity.getActor(),
                entity.getDetails(),
                entity.getCreatedAt()
        );
    }
}