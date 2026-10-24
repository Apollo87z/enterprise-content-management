package com.ecm.enterprisecontentmanagementbackend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "document_versions")
public class DocumentVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(
            name = "storage_key",
            nullable = false,
            unique = true,
            length = 255
    )
    private String storageKey;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Generated(event = EventType.INSERT)
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    protected DocumentVersionEntity() {
    }

    public DocumentVersionEntity(
            DocumentEntity document,
            Integer versionNumber,
            String storageKey,
            Long fileSize,
            String contentType
    ) {
        this.document = document;
        this.versionNumber = versionNumber;
        this.storageKey = storageKey;
        this.fileSize = fileSize;
        this.contentType = contentType;
    }

    public Long getId() {
        return id;
    }

    public DocumentEntity getDocument() {
        return document;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
