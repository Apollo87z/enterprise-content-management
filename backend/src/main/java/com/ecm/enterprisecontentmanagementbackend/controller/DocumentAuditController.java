package com.ecm.enterprisecontentmanagementbackend.controller;

import com.ecm.enterprisecontentmanagementbackend.dto.DocumentAuditResponse;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentAuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentAuditController {

    private final DocumentAuditService auditService;

    public DocumentAuditController(DocumentAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{id}/audit-logs")
    public Page<DocumentAuditResponse> getAuditLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return auditService.getDocumentAuditLogs(id, pageable)
                .map(DocumentAuditResponse::from);
    }
}