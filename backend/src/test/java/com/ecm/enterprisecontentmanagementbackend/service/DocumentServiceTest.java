
package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import com.ecm.enterprisecontentmanagementbackend.validation.DocumentFileValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository repository;

    @Mock
    private DocumentVersionRepository versionRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private DocumentFileValidator fileValidator;

    @Mock
    private DocumentAuditService auditService;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                repository,
                versionRepository,
                fileStorageService,
                fileValidator,
                auditService
        );
    }

    @Test
    void createDocument_savesDocument() {
        DocumentEntity document =
                new DocumentEntity("report.pdf", "application/pdf");

        when(repository.save(any(DocumentEntity.class)))
                .thenReturn(document);

        DocumentEntity result =
                service.createDocument("report.pdf", "application/pdf");

        assertEquals("report.pdf", result.getName());
        assertEquals("application/pdf", result.getContentType());

        verify(repository).save(any(DocumentEntity.class));

        verify(auditService).record(
                null,
                "DOCUMENT_CREATED",
                "system",
                "Document created: report.pdf"
        );
    }

    @Test
    void getDocumentById_existingDocument_returnsDocument() {
        DocumentEntity document =
                new DocumentEntity("report.pdf", "application/pdf");

        when(repository.findById(1L))
                .thenReturn(Optional.of(document));

        Optional<DocumentEntity> result =
                service.getDocumentById(1L);

        assertTrue(result.isPresent());
        assertEquals("report.pdf", result.get().getName());

        verifyNoInteractions(auditService);
    }

    @Test
    void getDocumentById_missingDocument_returnsEmpty() {
        when(repository.findById(99L))
                .thenReturn(Optional.empty());

        Optional<DocumentEntity> result =
                service.getDocumentById(99L);

        assertTrue(result.isEmpty());

        verifyNoInteractions(auditService);
    }

    @Test
    void deleteDocument_missingDocument_returnsFalse() {
        when(repository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        boolean deleted = service.deleteDocument(99L);

        assertFalse(deleted);

        verify(repository, never()).delete(any(DocumentEntity.class));
        verifyNoInteractions(
                versionRepository,
                fileStorageService,
                auditService
        );
    }

    @Test
    void deleteDocument_deletesVersionsBeforeParent() throws IOException {
        DocumentEntity document =
                new DocumentEntity("report.pdf", "application/pdf");

        document.setStorageKey("version-2-key");

        DocumentVersionEntity version1 =
                new DocumentVersionEntity(
                        document,
                        1,
                        "version-1-key",
                        100L,
                        "application/pdf"
                );

        DocumentVersionEntity version2 =
                new DocumentVersionEntity(
                        document,
                        2,
                        "version-2-key",
                        200L,
                        "application/pdf"
                );

        when(repository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(document));

        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(6L))
                .thenReturn(List.of(version2, version1));

        TransactionSynchronizationManager.initSynchronization();

        try {
            boolean deleted = service.deleteDocument(6L);

            assertTrue(deleted);

            InOrder order = inOrder(repository, versionRepository);

            order.verify(versionRepository)
                    .deleteAll(List.of(version2, version1));

            order.verify(versionRepository).flush();

            order.verify(repository).delete(document);

            order.verify(repository).flush();

            verify(auditService).record(
                    6L,
                    "DOCUMENT_DELETED",
                    "system",
                    "Document deleted: report.pdf"
            );

            verify(fileStorageService, never()).delete(anyString());

            assertEquals(
                    1,
                    TransactionSynchronizationManager
                            .getSynchronizations()
                            .size()
            );

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteDocument_afterCommit_removesBothFiles()
            throws IOException {

        DocumentEntity document =
                new DocumentEntity("report.pdf", "application/pdf");

        document.setStorageKey("version-2-key");

        DocumentVersionEntity version1 =
                new DocumentVersionEntity(
                        document,
                        1,
                        "version-1-key",
                        100L,
                        "application/pdf"
                );

        DocumentVersionEntity version2 =
                new DocumentVersionEntity(
                        document,
                        2,
                        "version-2-key",
                        200L,
                        "application/pdf"
                );

        when(repository.findByIdForUpdate(6L))
                .thenReturn(Optional.of(document));

        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(6L))
                .thenReturn(List.of(version2, version1));

        TransactionSynchronizationManager.initSynchronization();

        try {
            assertTrue(service.deleteDocument(6L));

            verify(auditService).record(
                    6L,
                    "DOCUMENT_DELETED",
                    "system",
                    "Document deleted: report.pdf"
            );

            verify(fileStorageService, never()).delete(anyString());

            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {

                synchronization.afterCommit();
            }

            verify(fileStorageService).delete("version-1-key");
            verify(fileStorageService).delete("version-2-key");

            verify(fileStorageService, times(2))
                    .delete(anyString());

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
