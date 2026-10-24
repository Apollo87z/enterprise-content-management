package com.ecm.enterprisecontentmanagementbackend.controller;

import com.ecm.enterprisecontentmanagementbackend.dto.DocumentRequest;
import com.ecm.enterprisecontentmanagementbackend.dto.DocumentResponse;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
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
            @PathVariable Long id) {

        return service.getDocumentById(id)
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(
            @Valid @RequestBody DocumentRequest request) {

        var document = service.createDocument(
                request.name(), request.contentType());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.from(document));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request) {

        return service.updateDocument(
                        id, request.name(), request.contentType())
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {

        if (service.deleteDocument(id)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}