package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import com.ecm.enterprisecontentmanagementbackend.validation.DocumentFileValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final FileStorageService fileStorageService;
    private final DocumentFileValidator fileValidator;

    public DocumentService(
            DocumentRepository repository,
            FileStorageService fileStorageService,
            DocumentFileValidator fileValidator
    ) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
    }

    public List<DocumentEntity> getAllDocuments() {
        return repository.findAll();
    }

    public Optional<DocumentEntity> getDocumentById(long id) {
        return repository.findById(id);
    }

    public DocumentEntity createDocument(String name, String contentType) {
        DocumentEntity document = new DocumentEntity(name, contentType);
        return repository.save(document);
    }

    public DocumentEntity uploadDocument(MultipartFile file) throws IOException {

        // Validate actual PDF/DOCX content before storing the file.
        fileValidator.validate(file);

        String contentType = file.getContentType();

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        // Remove any directory components from the client filename.
        originalName = originalName.replace("\\", "/");
        originalName = originalName.substring(
                originalName.lastIndexOf('/') + 1
        );

        if (originalName.isBlank() || originalName.length() > 255) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Store the actual file in the Docker-backed storage directory.
        String storageKey = fileStorageService.store(file);

        try {
            // Save document metadata in PostgreSQL.
            DocumentEntity document = new DocumentEntity(
                    originalName,
                    contentType
            );

            document.setStorageKey(storageKey);
            document.setFileSize(file.getSize());

            return repository.save(document);

        } catch (RuntimeException exception) {

            // Remove the stored file if saving metadata fails.
            try {
                fileStorageService.delete(storageKey);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    public Optional<Resource> downloadDocument(Long id) throws IOException {

        Optional<DocumentEntity> document = repository.findById(id);

        if (document.isEmpty() || document.get().getStorageKey() == null) {
            return Optional.empty();
        }

        return Optional.of(
                fileStorageService.load(document.get().getStorageKey())
        );
    }

    public Optional<DocumentEntity> updateDocument(
            Long id,
            String name,
            String contentType
    ) {
        return repository.findById(id).map(existing -> {

            existing.setName(name);
            existing.setContentType(contentType);

            return repository.save(existing);
        });
    }

    public boolean deleteDocument(Long id) {

        Optional<DocumentEntity> document = repository.findById(id);

        if (document.isEmpty()) {
            return false;
        }

        repository.delete(document.get());

        String storageKey = document.get().getStorageKey();

        if (storageKey != null) {
            try {
                fileStorageService.delete(storageKey);
            } catch (IOException exception) {
                logger.warn(
                        "Failed to delete stored file for document {}",
                        id,
                        exception
                );
            }
        }

        return true;
    }
}
