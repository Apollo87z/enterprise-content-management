package com.ecm.enterprisecontentmanagementbackend.controller;

import com.ecm.enterprisecontentmanagementbackend.dto.DocumentRequest;
import com.ecm.enterprisecontentmanagementbackend.dto.DocumentResponse;
import com.ecm.enterprisecontentmanagementbackend.dto.DocumentVersionResponse;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentService;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentVersionService;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
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

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;
    private final DocumentVersionService versionService;

    public DocumentController(
            DocumentService service,
            DocumentVersionService versionService
    ) {
        this.service = service;
        this.versionService = versionService;
    }

    @GetMapping
    public List<DocumentResponse> getAllDocuments() {
        return service.getAllDocuments()
                .stream()
                .map(DocumentResponse::from)
                .toList();
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
        DocumentEntity document = service.createDocument(
                request.name(),
                request.contentType()
        );

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
        return service.updateDocument(
                        id,
                        request.name(),
                        request.contentType())
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
