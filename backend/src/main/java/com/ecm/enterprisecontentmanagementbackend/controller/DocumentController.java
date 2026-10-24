package com.ecm.enterprisecontentmanagementbackend.controller;

import com.ecm.enterprisecontentmanagementbackend.dto.DocumentRequest;
import com.ecm.enterprisecontentmanagementbackend.dto.DocumentResponse;
import com.ecm.enterprisecontentmanagementbackend.dto.DocumentVersionResponse;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentService;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentVersionService;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "id",
                    "name",
                    "contentType",
                    "fileSize",
                    "category",
                    "createdAt",
                    "updatedAt"
            );

    private final DocumentService service;
    private final DocumentVersionService versionService;

    public DocumentController(
            DocumentService service,
            DocumentVersionService versionService
    ) {
        this.service = service;
        this.versionService = versionService;
    }

    public record DocumentPageResponse(
            List<DocumentResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {}

    @GetMapping
    public DocumentPageResponse getAllDocuments(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        String[] sortParts = sort.split(",", -1);

        if (sortParts.length != 2) {
            throw new IllegalArgumentException(
                    "Sort format must be field,asc or field,desc"
            );
        }

        String sortField = sortParts[0].trim();
        String sortDirection = sortParts[1].trim();

        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Unsupported sort field");
        }

        if (!sortDirection.equalsIgnoreCase("asc")
                && !sortDirection.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException(
                    "Sort direction must be asc or desc"
            );
        }

        Sort.Direction direction =
                Sort.Direction.fromString(sortDirection);

        Sort sorting = Sort.by(direction, sortField);

        if (!sortField.equals("id")) {
            sorting = sorting.and(Sort.by(Sort.Direction.ASC, "id"));
        }

        Pageable pageable = PageRequest.of(page, size, sorting);

        Page<DocumentResponse> results =
                service.searchDocuments(
                                name,
                                contentType,
                                category,
                                tag,
                                pageable
                        )
                        .map(DocumentResponse::from);

        return new DocumentPageResponse(
                results.getContent(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isFirst(),
                results.isLast()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable Long id
    ) {
        return service.getDocumentById(id)
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(
            @Valid @RequestBody DocumentRequest request
    ) {
        DocumentEntity document = service.createDocument(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.from(document));
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        DocumentEntity document = service.uploadDocument(file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.from(document));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long id
    ) throws IOException {
        var document = service.getDocumentById(id);

        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var resource = service.downloadDocument(id);

        if (resource.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return attachment(resource.get(), document.get().getName());
    }

    @PostMapping(
            value = "/{id}/versions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentVersionResponse> uploadVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return versionService.uploadVersion(id, file)
                .map(DocumentVersionResponse::from)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<DocumentVersionResponse>> listVersions(
            @PathVariable Long id
    ) {
        if (service.getDocumentById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<DocumentVersionResponse> versions =
                versionService.listVersions(id)
                        .stream()
                        .map(DocumentVersionResponse::from)
                        .toList();

        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{id}/versions/{version}/download")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable Long id,
            @PathVariable Integer version
    ) throws IOException {
        var versionEntity = versionService.getVersion(id, version);

        if (versionEntity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var resource = versionService.downloadVersion(id, version);

        if (resource.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String filename = service.getDocumentById(id)
                .map(DocumentEntity::getName)
                .orElse("document");

        return attachment(resource.get(), filename);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request
    ) {
        return service.updateDocument(id, request)
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id
    ) {
        if (service.deleteDocument(id)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<Resource> attachment(
            Resource resource,
            String filename
    ) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}