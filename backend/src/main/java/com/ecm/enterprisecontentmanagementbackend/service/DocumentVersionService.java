
package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;

import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import com.ecm.enterprisecontentmanagementbackend.validation.DocumentFileValidator;
import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentVersionService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentVersionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FileStorageService storageService;
    private final DocumentFileValidator fileValidator;
    private final EntityManager entityManager;

    public DocumentVersionService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            FileStorageService storageService,
            DocumentFileValidator fileValidator,
            EntityManager entityManager
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
        this.entityManager = entityManager;
    }

    @Transactional(rollbackFor = IOException.class)
    public Optional<DocumentVersionEntity> uploadVersion(
            Long documentId,
            MultipartFile file
    ) throws IOException {

        fileValidator.validate(file);

        Optional<DocumentEntity> existing =
                documentRepository.findByIdForUpdate(documentId);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        DocumentEntity document = existing.get();

        int nextVersion = versionRepository
                .findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);

        String storageKey = storageService.store(file);

        // Delete the newly stored file if the database transaction fails.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                storageService.delete(storageKey);
                            } catch (IOException ex) {
                                log.warn(
                                        "Failed to clean up file {} after rollback",
                                        storageKey,
                                        ex
                                );
                            }
                        }
                    }
                }
        );

        DocumentVersionEntity version = new DocumentVersionEntity(
                document,
                nextVersion,
                storageKey,
                file.getSize(),
                file.getContentType()
        );

        // Execute INSERT and reload PostgreSQL-generated created_at.
        versionRepository.saveAndFlush(version);
        entityManager.refresh(version);

        // Point the document to its newest version.
        document.setStorageKey(storageKey);
        document.setFileSize(file.getSize());
        document.setContentType(file.getContentType());

        documentRepository.save(document);

        return Optional.of(version);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionEntity> listVersions(Long documentId) {
        return versionRepository
                .findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentVersionEntity> getVersion(
            Long documentId,
            Integer versionNumber
    ) {
        return versionRepository
                .findByDocumentIdAndVersionNumber(
                        documentId,
                        versionNumber
                );
    }

    @Transactional(readOnly = true)
    public Optional<Resource> downloadVersion(
            Long documentId,
            Integer versionNumber
    ) throws IOException {

        Optional<DocumentVersionEntity> version =
                versionRepository.findByDocumentIdAndVersionNumber(
                        documentId,
                        versionNumber
                );

        if (version.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                storageService.load(version.get().getStorageKey())
        );
    }
}
