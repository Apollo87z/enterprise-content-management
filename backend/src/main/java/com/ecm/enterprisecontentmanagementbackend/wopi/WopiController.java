package com.ecm.enterprisecontentmanagementbackend.wopi;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/wopi/files")
public class WopiController {
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final WopiTokenService tokens;
    private final FileStorageService storage;
    private final WopiEditingService editing;
    public WopiController(DocumentRepository documents, DocumentVersionRepository versions,
                          WopiTokenService tokens, FileStorageService storage, WopiEditingService editing) {
        this.documents = documents; this.versions = versions; this.tokens = tokens;
        this.storage = storage; this.editing = editing;
    }
    private WopiTokenService.WopiAccess authenticate(String token, Long id) {
        try { return tokens.validateToken(token, id); }
        catch (RuntimeException ex) { return null; }
    }
    @GetMapping("/{id}")
    public ResponseEntity<WopiFileInfo> checkFileInfo(@PathVariable Long id,
                                                     @RequestParam("access_token") String token) {
        var access = authenticate(token, id);
        if (access == null) return ResponseEntity.status(401).build();
        DocumentEntity doc = documents.findById(id).orElse(null);
        if (doc == null || doc.getStorageKey() == null) return ResponseEntity.notFound().build();
        int version = versions.findTopByDocumentIdOrderByVersionNumberDesc(id)
                .map(v -> v.getVersionNumber()).orElse(1);
        return ResponseEntity.ok(new WopiFileInfo(doc.getName(), doc.getFileSize() == null ? 0 : doc.getFileSize(),
                Integer.toString(version), "ecm", access.username(), access.username(),
                access.canWrite(), access.canWrite(), access.canWrite()));
    }
    @GetMapping("/{id}/contents")
    public ResponseEntity<Resource> getFile(@PathVariable Long id,
                                            @RequestParam("access_token") String token) {
        if (authenticate(token, id) == null) return ResponseEntity.status(401).build();
        DocumentEntity doc = documents.findById(id).orElse(null);
        if (doc == null || doc.getStorageKey() == null) return ResponseEntity.notFound().build();
        try {
            Resource file = storage.load(doc.getStorageKey());
            MediaType type;
            try { type = MediaType.parseMediaType(doc.getContentType()); }
            catch (Exception ex) { type = MediaType.APPLICATION_OCTET_STREAM; }
            return ResponseEntity.ok().contentType(type).body(file);
        } catch (IOException ex) { return ResponseEntity.status(500).build(); }
    }
    @PostMapping("/{id}")
    public ResponseEntity<Void> lockOperations(@PathVariable Long id,
            @RequestParam("access_token") String token,
            @RequestHeader(value = "X-WOPI-Override", required = false) String operation,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lock,
            @RequestHeader(value = "X-WOPI-OldLock", required = false) String oldLock) {
        var access = authenticate(token, id);
        if (access == null) return ResponseEntity.status(401).build();
        if (!access.canWrite()) return ResponseEntity.status(403).build();
        if (operation == null) return ResponseEntity.badRequest().build();
        WopiEditingService.Result result = switch (operation.toUpperCase(java.util.Locale.ROOT)) {
            case "LOCK" -> editing.lock(id, lock, oldLock);
            case "REFRESH_LOCK" -> editing.refresh(id, lock);
            case "UNLOCK" -> editing.unlock(id, lock);
            case "GET_LOCK" -> editing.getLock(id);
            default -> new WopiEditingService.Result(501, null, null);
        };
        return response(result);
    }
    @PostMapping("/{id}/contents")
    public ResponseEntity<Void> putFile(@PathVariable Long id,
            @RequestParam("access_token") String token,
            @RequestHeader(value = "X-WOPI-Override", required = false) String operation,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lock,
            jakarta.servlet.http.HttpServletRequest request) throws IOException {
        var access = authenticate(token, id);
        if (access == null) return ResponseEntity.status(401).build();
        if (!access.canWrite()) return ResponseEntity.status(403).build();
        if (!"PUT".equalsIgnoreCase(operation)) return ResponseEntity.badRequest().build();
        if (request.getContentLengthLong() > 50L * 1024 * 1024) return ResponseEntity.status(413).build();
        try (InputStream in = new java.io.FilterInputStream(request.getInputStream()) {
            long count;
            @Override public int read() throws IOException {
                int b = super.read();
                if (b != -1 && ++count > 50L * 1024 * 1024) throw new IOException("WOPI upload exceeds 50 MiB");
                return b;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                int n = super.read(b, off, len);
                if (n > 0 && (count += n) > 50L * 1024 * 1024) throw new IOException("WOPI upload exceeds 50 MiB");
                return n;
            }
        }) {
            return response(editing.save(id, lock, in, access.username()));
        }
    }
    private ResponseEntity<Void> response(WopiEditingService.Result result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status());
        if (result.currentLock() != null) builder.header("X-WOPI-Lock", result.currentLock());
        if (result.version() != null) builder.header("X-WOPI-ItemVersion", result.version());
        if (result.status() == 409) builder.header("X-WOPI-LockFailureReason", "Lock mismatch");
        return builder.build();
    }
}
