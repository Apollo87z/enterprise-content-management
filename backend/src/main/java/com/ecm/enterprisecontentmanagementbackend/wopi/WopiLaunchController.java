package com.ecm.enterprisecontentmanagementbackend.wopi;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/v1/documents")
public class WopiLaunchController {

    private final DocumentRepository documents;
    private final WopiTokenService tokens;

    public WopiLaunchController(
            DocumentRepository documents,
            WopiTokenService tokens
    ) {
        this.documents = documents;
        this.tokens = tokens;
    }

    @PostMapping("/{id}/wopi-token")
    @ResponseBody
    public ResponseEntity<WopiTokenService.WopiToken> issueToken(
            @PathVariable Long id,
            Authentication authentication
    ) {
        DocumentEntity document = documents.findById(id).orElse(null);

        if (document == null || document.getStorageKey() == null) {
            return ResponseEntity.notFound().build();
        }

        boolean canWrite = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN") ||
                        authority.getAuthority().equals("ROLE_EDITOR")
                );

        return ResponseEntity.ok(
                tokens.generateToken(
                        id,
                        authentication.getName(),
                        canWrite
                )
        );
    }
}
