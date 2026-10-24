package com.ecm.enterprisecontentmanagementbackend.dto;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;

public record DocumentResponse(
        Long id,
        String name,
        String contentType
) {
    public static DocumentResponse from(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getName(),
                entity.getContentType()
        );
    }
}