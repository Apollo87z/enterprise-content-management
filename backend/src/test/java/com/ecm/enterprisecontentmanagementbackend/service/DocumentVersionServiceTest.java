
package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;
import com.ecm.enterprisecontentmanagementbackend.validation.DocumentFileValidator;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentVersionServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository versionRepository;

    @Mock
    private FileStorageService storageService;

    @Mock
    private DocumentFileValidator fileValidator;

    @Mock
    private EntityManager entityManager;

    private DocumentVersionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentVersionService(
                documentRepository,
                versionRepository,
                storageService,
                fileValidator,
                entityManager
        );
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                "%PDF-1.4\nTest content\n%%EOF".getBytes()
        );
    }

    private DocumentEntity document() {
        return new DocumentEntity("report.pdf", "application/pdf");
    }

    @Test
    void uploadVersion_missingDocument_returnsEmpty() throws IOException {

        MockMultipartFile file = pdfFile();

        when(documentRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        Optional<DocumentVersionEntity> result =
                service.uploadVersion(99L, file);

        assertTrue(result.isEmpty());

        verify(fileValidator).validate(file);
        verifyNoInteractions(storageService, versionRepository, entityManager);
    }

    @Test
    void uploadVersion_firstVersion_usesVersionOne() throws IOException {

        MockMultipartFile file = pdfFile();
        DocumentEntity document = document();

        when(documentRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(document));

        when(versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(1L))
                .thenReturn(Optional.empty());

        when(storageService.store(file))
                .thenReturn("new-storage-key");

        TransactionSynchronizationManager.initSynchronization();

        try {
            Optional<DocumentVersionEntity> result =
                    service.uploadVersion(1L, file);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().getVersionNumber());
            assertEquals("new-storage-key", result.get().getStorageKey());

            verify(versionRepository).saveAndFlush(result.get());
            verify(entityManager).refresh(result.get());

            assertEquals("new-storage-key", document.getStorageKey());
            assertEquals(file.getSize(), document.getFileSize());
            assertEquals("application/pdf", document.getContentType());

            verify(documentRepository).save(document);

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadVersion_existingVersion_incrementsVersionNumber()
            throws IOException {

        MockMultipartFile file = pdfFile();
        DocumentEntity document = document();

        DocumentVersionEntity previousVersion =
                new DocumentVersionEntity(
                        document,
                        2,
                        "previous-key",
                        100L,
                        "application/pdf"
                );

        when(documentRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(document));

        when(versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(5L))
                .thenReturn(Optional.of(previousVersion));

        when(storageService.store(file))
                .thenReturn("version-3-key");

        TransactionSynchronizationManager.initSynchronization();

        try {
            Optional<DocumentVersionEntity> result =
                    service.uploadVersion(5L, file);

            assertTrue(result.isPresent());
            assertEquals(3, result.get().getVersionNumber());

            verify(versionRepository).saveAndFlush(result.get());
            verify(entityManager).refresh(result.get());

            assertEquals("version-3-key", document.getStorageKey());
            verify(documentRepository).save(document);

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadVersion_rollback_deletesNewFile() throws IOException {

        MockMultipartFile file = pdfFile();
        DocumentEntity document = document();

        when(documentRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(document));

        when(versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(5L))
                .thenReturn(Optional.empty());

        when(storageService.store(file))
                .thenReturn("rollback-key");

        TransactionSynchronizationManager.initSynchronization();

        try {
            assertTrue(service.uploadVersion(5L, file).isPresent());

            verify(storageService, never()).delete(anyString());

            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {

                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                );
            }

            verify(storageService).delete("rollback-key");

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadVersion_successfulCommit_keepsNewFile() throws IOException {

        MockMultipartFile file = pdfFile();
        DocumentEntity document = document();

        when(documentRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(document));

        when(versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(5L))
                .thenReturn(Optional.empty());

        when(storageService.store(file))
                .thenReturn("committed-key");

        TransactionSynchronizationManager.initSynchronization();

        try {
            assertTrue(service.uploadVersion(5L, file).isPresent());

            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {

                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                );
            }

            verify(storageService, never()).delete(anyString());

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void listVersions_returnsVersionHistory() {

        DocumentEntity document = document();

        DocumentVersionEntity version1 =
                new DocumentVersionEntity(
                        document, 1, "key-1", 100L, "application/pdf"
                );

        DocumentVersionEntity version2 =
                new DocumentVersionEntity(
                        document, 2, "key-2", 200L, "application/pdf"
                );

        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(5L))
                .thenReturn(List.of(version2, version1));

        List<DocumentVersionEntity> result = service.listVersions(5L);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersionNumber());
        assertEquals(1, result.get(1).getVersionNumber());
    }

    @Test
    void getVersion_existingVersion_returnsVersion() {

        DocumentVersionEntity version =
                new DocumentVersionEntity(
                        document(), 2, "key-2", 200L, "application/pdf"
                );

        when(versionRepository.findByDocumentIdAndVersionNumber(5L, 2))
                .thenReturn(Optional.of(version));

        Optional<DocumentVersionEntity> result =
                service.getVersion(5L, 2);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getVersionNumber());
    }

    @Test
    void getVersion_missingVersion_returnsEmpty() {

        when(versionRepository.findByDocumentIdAndVersionNumber(5L, 99))
                .thenReturn(Optional.empty());

        assertTrue(service.getVersion(5L, 99).isEmpty());
    }

    @Test
    void downloadVersion_existingVersion_returnsFile() throws IOException {

        DocumentVersionEntity version =
                new DocumentVersionEntity(
                        document(), 2, "key-2", 200L, "application/pdf"
                );

        Resource resource = new ByteArrayResource("PDF content".getBytes());

        when(versionRepository.findByDocumentIdAndVersionNumber(5L, 2))
                .thenReturn(Optional.of(version));

        when(storageService.load("key-2"))
                .thenReturn(resource);

        Optional<Resource> result = service.downloadVersion(5L, 2);

        assertTrue(result.isPresent());
        assertSame(resource, result.get());

        verify(storageService).load("key-2");
    }

    @Test
    void downloadVersion_missingVersion_returnsEmpty() throws IOException {

        when(versionRepository.findByDocumentIdAndVersionNumber(5L, 99))
                .thenReturn(Optional.empty());

        Optional<Resource> result = service.downloadVersion(5L, 99);

        assertTrue(result.isEmpty());

        verifyNoInteractions(storageService);
    }
}
