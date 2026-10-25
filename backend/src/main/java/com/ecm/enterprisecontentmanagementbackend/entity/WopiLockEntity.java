package com.ecm.enterprisecontentmanagementbackend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "wopi_locks")
public class WopiLockEntity {
    @Id @Column(name = "document_id") private Long documentId;
    @Column(name = "lock_value", nullable = false, length = 1024) private String lockValue;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    protected WopiLockEntity() {}
    public WopiLockEntity(Long documentId, String lockValue, OffsetDateTime expiresAt) {
        this.documentId = documentId; this.lockValue = lockValue; this.expiresAt = expiresAt;
    }
    public String getLockValue() { return lockValue; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setLockValue(String value) { lockValue = value; }
    public void setExpiresAt(OffsetDateTime value) { expiresAt = value; }
}
