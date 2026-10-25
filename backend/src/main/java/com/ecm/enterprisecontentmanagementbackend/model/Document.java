package com.ecm.enterprisecontentmanagementbackend.model;

public record Document(
        Long id,
        String name,
        String contentType
) {}