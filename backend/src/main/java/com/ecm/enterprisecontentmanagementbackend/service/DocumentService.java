
package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import com.ecm.enterprisecontentmanagementbackend.validation.DocumentFileValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final DocumentVersionRepository versionRepository;
    private final FileStorageService fileStorageService;
    private final DocumentFileValidator fileValidator;

    public DocumentService(
            DocumentRepository repository,
            DocumentVersionRepository versionRepository,
            FileStorageService fileStorageService,
            DocumentFileValidator fileValidator
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
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

    @Transactional(rollbackFor = IOException.class)
    public DocumentEntity uploadDocument(MultipartFile file) throws IOException {

        fileValidator.validate(file);

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        // Remove directory components from the uploaded filename.
        originalName = originalName.replace("\\", "/");
        originalName = originalName.substring(
                originalName.lastIndexOf('/') + 1
        );

        if (originalName.isBlank() || originalName.length() > 255) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String storageKey = fileStorageService.store(file);

        // Remove the uploaded file if the database transaction rolls back.
        registerRollbackCleanup(storageKey);

        DocumentEntity document = new DocumentEntity(
                originalName,
                file.getContentType()
        );

        document.setStorageKey(storageKey);
        document.setFileSize(file.getSize());

        document = repository.save(document);

        // Every uploaded document starts with version 1.
        DocumentVersionEntity firstVersion = new DocumentVersionEntity(
                document,
                1,
                storageKey,
                file.getSize(),
                file.getContentType()
        );

        versionRepository.save(firstVersion);

        return document;
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

            // Do not change the MIME type of an already stored file.
            if (existing.getStorageKey() == null) {
                existing.setContentType(contentType);
            }

            return repository.save(existing);
        });
    }

    @Transactional
    public boolean deleteDocument(Long id) {

        // Lock the document to prevent concurrent version uploads.
        Optional<DocumentEntity> existing =
                repository.findByIdForUpdate(id);

        if (existing.isEmpty()) {
            return false;
        }

        DocumentEntity document = existing.get();

        // Load the version records before deleting anything.
        List<DocumentVersionEntity> versions =
                versionRepository.findByDocumentIdOrderByVersionNumberDesc(id);

        // Record all physical files that need to be removed after commit.
        List<String> storageKeys = new ArrayList<>(
                versions.stream()
                        .map(DocumentVersionEntity::getStorageKey)
                        .toList()
        );

        // Support legacy documents without version history.
        if (document.getStorageKey() != null
                && !storageKeys.contains(document.getStorageKey())) {

            storageKeys.add(document.getStorageKey());
        }

        // Delete child entities before deleting the parent document.
        // This avoids Hibernate's transient-reference exception.
        versionRepository.deleteAll(versions);
        versionRepository.flush();

        repository.delete(document);
        repository.flush();

        // Delete physical files only after the database commit succeeds.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        for (String key : storageKeys) {
                            try {
                                fileStorageService.delete(key);

                                logger.info(
                                        "Deleted stored file {} for document {}",
                                        key,
                                        id
                                );

                            } catch (IOException exception) {

                                logger.warn(
                                        "Failed to delete stored file {} for document {}",
                                        key,
                                        id,
                                        exception
                                );
                            }
                        }
                    }
                }
        );

        return true;
    }

    private void registerRollbackCleanup(String storageKey) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        if (status != STATUS_COMMITTED) {
                            try {
                                fileStorageService.delete(storageKey);

                            } catch (IOException exception) {

                                logger.warn(
                                        "Failed to clean up uploaded file {}",
                                        storageKey,
                                        exception
                                );
                            }
                        }
                    }
                }
        );
    }
}
