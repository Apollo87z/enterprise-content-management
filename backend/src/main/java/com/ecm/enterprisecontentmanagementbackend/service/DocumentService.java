
package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.dto.DocumentRequest;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import com.ecm.enterprisecontentmanagementbackend.validation.DocumentFileValidator;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DocumentService {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final DocumentVersionRepository versionRepository;
    private final FileStorageService fileStorageService;
    private final DocumentFileValidator fileValidator;
    private final DocumentAuditService auditService;

    public DocumentService(
            DocumentRepository repository,
            DocumentVersionRepository versionRepository,
            FileStorageService fileStorageService,
            DocumentFileValidator fileValidator,
            DocumentAuditService auditService
    ) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.auditService = auditService;
    }

    public List<DocumentEntity> getAllDocuments() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<DocumentEntity> searchDocuments(
            String name,
            String contentType,
            Pageable pageable
    ) {
        return searchDocuments(name, contentType, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentEntity> searchDocuments(
            String name,
            String contentType,
            String category,
            String tag,
            Pageable pageable
    ) {
        Specification<DocumentEntity> specification =
                (root, query, cb) -> cb.conjunction();

        if (name != null && !name.isBlank()) {
            String normalizedName = name.trim().toLowerCase(Locale.ROOT);

            String escapedName = normalizedName
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");

            specification = specification.and(
                    (root, query, cb) ->
                            cb.like(
                                    cb.lower(root.get("name")),
                                    "%" + escapedName + "%",
                                    '\\'
                            )
            );
        }

        if (contentType != null && !contentType.isBlank()) {
            String normalizedContentType = contentType.trim();

            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("contentType"),
                                    normalizedContentType
                            )
            );
        }

        if (category != null && !category.isBlank()) {
            String normalizedCategory = category.trim();

            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("category"),
                                    normalizedCategory
                            )
            );
        }

        if (tag != null && !tag.isBlank()) {
            String normalizedTag = tag.trim();

            specification = specification.and(
                    (root, query, cb) -> {
                        HibernateCriteriaBuilder hcb =
                                (HibernateCriteriaBuilder) cb;

                        return hcb.isTrue(
                                hcb.sql(
                                        "? @> ARRAY[?]::text[]",
                                        Boolean.class,
                                        root.get("tags"),
                                        hcb.literal(normalizedTag)
                                )
                        );
                    }
            );
        }

        return repository.findAll(specification, pageable);
    }

    public Optional<DocumentEntity> getDocumentById(long id) {
        return repository.findById(id);
    }

    @Transactional
    public DocumentEntity createDocument(DocumentRequest request) {
        DocumentEntity document = new DocumentEntity(
                request.name(),
                request.contentType()
        );

        document.setDescription(request.description());
        document.setCategory(request.category());
        document.setTags(request.tags());

        DocumentEntity saved = repository.save(document);

        auditService.record(
                saved.getId(),
                "DOCUMENT_CREATED",
                "system",
                "Document created: " + saved.getName()
        );

        return saved;
    }

    @Transactional
    public DocumentEntity createDocument(String name, String contentType) {
        return createDocument(new DocumentRequest(name, contentType));
    }

    @Transactional(rollbackFor = IOException.class)
    public DocumentEntity uploadDocument(MultipartFile file) throws IOException {
        fileValidator.validate(file);

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        originalName = originalName.replace("\\", "/");
        originalName = originalName.substring(
                originalName.lastIndexOf('/') + 1
        );

        if (originalName.isBlank() || originalName.length() > 255) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String storageKey = fileStorageService.store(file);

        registerRollbackCleanup(storageKey);

        DocumentEntity document = new DocumentEntity(
                originalName,
                file.getContentType()
        );

        document.setStorageKey(storageKey);
        document.setFileSize(file.getSize());

        document = repository.save(document);

        DocumentVersionEntity firstVersion = new DocumentVersionEntity(
                document,
                1,
                storageKey,
                file.getSize(),
                file.getContentType()
        );

        versionRepository.save(firstVersion);

        auditService.record(
                document.getId(),
                "DOCUMENT_UPLOADED",
                "system",
                "Document uploaded: " + document.getName()
        );

        return document;
    }

    @Transactional
    public Optional<Resource> downloadDocument(Long id) throws IOException {
        Optional<DocumentEntity> document = repository.findById(id);

        if (document.isEmpty() || document.get().getStorageKey() == null) {
            return Optional.empty();
        }

        Resource resource =
                fileStorageService.load(document.get().getStorageKey());

        auditService.record(
                id,
                "DOCUMENT_DOWNLOADED",
                "system",
                "Document download requested: " + document.get().getName()
        );

        return Optional.of(resource);
    }

    @Transactional
    public Optional<DocumentEntity> updateDocument(
            Long id,
            DocumentRequest request
    ) {
        return repository.findById(id).map(existing -> {
            existing.setName(request.name());

            if (existing.getStorageKey() == null) {
                existing.setContentType(request.contentType());
            }

            existing.setDescription(request.description());
            existing.setCategory(request.category());
            existing.setTags(request.tags());

            DocumentEntity saved = repository.save(existing);

            auditService.record(
                    saved.getId(),
                    "DOCUMENT_UPDATED",
                    "system",
                    "Document metadata updated: " + saved.getName()
            );

            return saved;
        });
    }

    @Transactional
    public boolean deleteDocument(Long id) {
        Optional<DocumentEntity> existing =
                repository.findByIdForUpdate(id);

        if (existing.isEmpty()) {
            return false;
        }

        DocumentEntity document = existing.get();

        List<DocumentVersionEntity> versions =
                versionRepository.findByDocumentIdOrderByVersionNumberDesc(id);

        List<String> storageKeys = new ArrayList<>(
                versions.stream()
                        .map(DocumentVersionEntity::getStorageKey)
                        .toList()
        );

        if (document.getStorageKey() != null
                && !storageKeys.contains(document.getStorageKey())) {
            storageKeys.add(document.getStorageKey());
        }

        auditService.record(
                id,
                "DOCUMENT_DELETED",
                "system",
                "Document deleted: " + document.getName()
        );

        versionRepository.deleteAll(versions);
        versionRepository.flush();

        repository.delete(document);
        repository.flush();

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
