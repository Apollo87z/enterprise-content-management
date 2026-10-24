package com.ecm.enterprisecontentmanagementbackend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Arrays;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "storage_key", unique = true, length = 255)
    private String storageKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(String name, String contentType) {
        this.name = name;
        this.contentType = contentType;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (tags == null) {
            tags = new String[0];
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String[] getTags() {
        return tags == null ? new String[0] : Arrays.copyOf(tags, tags.length);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setTags(String[] tags) {
        this.tags = tags == null
                ? new String[0]
                : Arrays.copyOf(tags, tags.length);
    }
}
