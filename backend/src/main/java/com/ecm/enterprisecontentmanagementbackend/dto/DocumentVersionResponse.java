package com.ecm.enterprisecontentmanagementbackend.dto;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import java.time.OffsetDateTime;

public record DocumentVersionResponse(
        Long id,
        Long documentId,
        Integer versionNumber,
        String contentType,
        Long fileSize,
        OffsetDateTime createdAt
) {
    public static DocumentVersionResponse from(DocumentVersionEntity version) {
        return new DocumentVersionResponse(
                version.getId(),
                version.getDocument().getId(),
                version.getVersionNumber(),
                version.getContentType(),
                version.getFileSize(),
                version.getCreatedAt()
        );
    }
}
