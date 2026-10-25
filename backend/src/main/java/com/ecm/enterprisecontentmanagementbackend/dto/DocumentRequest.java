package com.ecm.enterprisecontentmanagementbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(

        @NotBlank(message = "Document name is required")
        @Size(max = 255)
        String name,

        @NotBlank(message = "Content type is required")
        @Size(max = 255)
        String contentType,

        String description,

        @Size(max = 100)
        String category,

        @Size(max = 20, message = "Maximum 20 tags allowed")
        String[] tags

) {
    public DocumentRequest(String name, String contentType) {
        this(name, contentType, null, null, null);
    }
}
