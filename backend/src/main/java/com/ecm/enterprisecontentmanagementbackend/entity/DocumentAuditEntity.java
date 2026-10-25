package com.ecm.enterprisecontentmanagementbackend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "document_audit_logs")
public class DocumentAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected DocumentAuditEntity() {
    }

    public DocumentAuditEntity(
            Long documentId,
            String action,
            String actor,
            String details
    ) {
        this.documentId = documentId;
        this.action = action;
        this.actor = actor;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}