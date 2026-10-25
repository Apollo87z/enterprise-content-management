package com.ecm.enterprisecontentmanagementbackend.dto;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;

import java.time.OffsetDateTime;

public record DocumentResponse(
        Long id,
        String name,
        String contentType,
        String description,
        String category,
        String[] tags,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DocumentResponse from(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getName(),
                entity.getContentType(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getTags(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
