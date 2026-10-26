package com.ecm.enterprisecontentmanagementbackend.wopi;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class WopiEditingService {
    private static final Logger log = LoggerFactory.getLogger(WopiEditingService.class);
    private static final int LOCK_MINUTES = 30;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final FileStorageService storage;
    private final JdbcTemplate jdbc;

    public WopiEditingService(DocumentRepository documents,
                              DocumentVersionRepository versions,
                              FileStorageService storage,
                              JdbcTemplate jdbc) {
        this.documents = documents;
        this.versions = versions;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    public record Result(int status, String currentLock, String version) {}

    private record LockState(String value, OffsetDateTime expiresAt) {}

    // Every operation serializes on the SAME documents row, including when no wopi_locks row exists.
    private DocumentEntity documentForUpdate(Long id) {
        return documents.findByIdForUpdate(id).orElse(null);
    }

    private LockState activeLock(Long id) {
        return jdbc.query("SELECT lock_value, expires_at FROM wopi_locks WHERE document_id = ?",
                rs -> rs.next() ? new LockState(rs.getString(1),
                        rs.getObject(2, OffsetDateTime.class)) : null, id);
    }

    private LockState currentLock(Long id) {
        LockState state = activeLock(id);
        if (state != null && !state.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            jdbc.update("DELETE FROM wopi_locks WHERE document_id = ?", id);
            return null;
        }
        return state;
    }

    private boolean valid(String lock) {
        return lock != null && !lock.isEmpty() && lock.length() <= 1024;
    }

    private Result conflict(LockState state) {
        return new Result(409, state == null ? "" : state.value(), null);
    }

    private OffsetDateTime expiry() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(LOCK_MINUTES);
    }

    @Transactional
    public Result lock(Long id, String lock, String oldLock) {
        if (!valid(lock) || (oldLock != null && !valid(oldLock))) return new Result(400, null, null);
        if (documentForUpdate(id) == null) return new Result(404, null, null);
        LockState current = currentLock(id);
        if (oldLock != null) {
            // WOPI unlock-and-relock: replace only the exact currently held lock.
            if (current == null || !Objects.equals(current.value(), oldLock)) return conflict(current);
            jdbc.update("UPDATE wopi_locks SET lock_value = ?, expires_at = ? WHERE document_id = ?",
                    lock, expiry(), id);
            return new Result(200, null, null);
        }
        if (current != null) {
            if (!Objects.equals(current.value(), lock)) return conflict(current);
            jdbc.update("UPDATE wopi_locks SET expires_at = ? WHERE document_id = ?", expiry(), id);
            return new Result(200, null, null);
        }
        jdbc.update("INSERT INTO wopi_locks (document_id, lock_value, expires_at) VALUES (?, ?, ?)",
                id, lock, expiry());
        return new Result(200, null, null);
    }

    @Transactional
    public Result refresh(Long id, String lock) {
        if (!valid(lock)) return new Result(400, null, null);
        if (documentForUpdate(id) == null) return new Result(404, null, null);
        LockState current = currentLock(id);
        if (current == null || !Objects.equals(current.value(), lock)) return conflict(current);
        jdbc.update("UPDATE wopi_locks SET expires_at = ? WHERE document_id = ?", expiry(), id);
        return new Result(200, null, null);
    }

    @Transactional
    public Result unlock(Long id, String lock) {
        if (!valid(lock)) return new Result(400, null, null);
        if (documentForUpdate(id) == null) return new Result(404, null, null);
        LockState current = currentLock(id);
        if (current == null || !Objects.equals(current.value(), lock)) return conflict(current);
        jdbc.update("DELETE FROM wopi_locks WHERE document_id = ?", id);
        return new Result(200, null, null);
    }

    @Transactional
    public Result getLock(Long id) {
        if (documentForUpdate(id) == null) return new Result(404, null, null);
        LockState current = currentLock(id);
        return new Result(200, current == null ? "" : current.value(), null);
    }

    @Transactional(rollbackFor = IOException.class)
    public Result save(Long id, String suppliedLock, InputStream input, String username) throws IOException {
        DocumentEntity document = documentForUpdate(id);
        if (document == null || document.getStorageKey() == null) return new Result(404, null, null);
        LockState current = currentLock(id);
        // When a lock exists, PutFile MUST carry exactly that lock.
        if (current != null && !Objects.equals(current.value(), suppliedLock)) return conflict(current);
        // A stale lock header must not silently turn into an unlocked save.
        if (current == null && suppliedLock != null && !suppliedLock.isEmpty()) return conflict(null);

        // This method is transactional and the documents row remains locked through commit.
        String newKey = storage.store(input);
        registerRollbackCleanup(newKey);
        long newSize = storage.load(newKey).contentLength();
        int nextVersion = versions.findTopByDocumentIdOrderByVersionNumberDesc(id)
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        versions.save(new DocumentVersionEntity(document, nextVersion, newKey,
                newSize, document.getContentType()));
        document.setStorageKey(newKey);
        document.setFileSize(newSize);
        documents.save(document);
        // Force constraint violations before the transaction returns.
        versions.flush();
        documents.flush();
        log.info("WOPI saved document {} version {} by {}", id, nextVersion, username);
        return new Result(200, null, Integer.toString(nextVersion));
    }

    private void registerRollbackCleanup(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("WOPI save requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { storage.delete(key); }
                    catch (IOException ex) { log.error("Failed to clean up rolled-back WOPI file {}", key, ex); }
                }
            }
        });
    }
}
