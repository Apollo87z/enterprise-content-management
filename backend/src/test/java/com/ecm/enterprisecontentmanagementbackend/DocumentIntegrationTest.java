
package com.ecm.enterprisecontentmanagementbackend;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.entity.DocumentVersionEntity;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentVersionRepository;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentService;
import com.ecm.enterprisecontentmanagementbackend.service.DocumentVersionService;
import com.ecm.enterprisecontentmanagementbackend.storage.FileStorageService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DocumentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentVersionService versionService;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    DocumentVersionRepository versionRepository;

    @Autowired
    FileStorageService storageService;

    private MockMultipartFile pdf(String name, String text) {

        byte[] bytes = (
                "%PDF-1.4\n" +
                        "1 0 obj\n" +
                        "<< /Type /Catalog >>\n" +
                        "endobj\n" +
                        text + "\n" +
                        "%%EOF"
        ).getBytes(StandardCharsets.UTF_8);

        return new MockMultipartFile(
                "file",
                name,
                "application/pdf",
                bytes
        );
    }

    @Test
    void documentLifecycle_uploadVersionDownloadDelete() throws Exception {

        // STEP 1: Upload first document
        MockMultipartFile firstFile = pdf(
                "report.pdf",
                "First version"
        );

        DocumentEntity document =
                documentService.uploadDocument(firstFile);

        Long documentId = document.getId();

        assertNotNull(documentId);

        assertTrue(
                documentRepository.existsById(documentId)
        );

        // STEP 2: Verify version 1 was created
        List<DocumentVersionEntity> firstVersions =
                versionService.listVersions(documentId);

        assertEquals(1, firstVersions.size());

        assertEquals(
                1,
                firstVersions.get(0).getVersionNumber()
        );

        // STEP 3: Upload version 2
        MockMultipartFile secondFile = pdf(
                "report-v2.pdf",
                "Second version"
        );

        DocumentVersionEntity secondVersion =
                versionService.uploadVersion(documentId, secondFile)
                        .orElseThrow();

        assertEquals(
                2,
                secondVersion.getVersionNumber()
        );

        assertNotNull(secondVersion.getCreatedAt());

        // STEP 4: Verify version history
        List<DocumentVersionEntity> versions =
                versionService.listVersions(documentId);

        assertEquals(2, versions.size());

        assertEquals(
                2,
                versions.get(0).getVersionNumber()
        );

        assertEquals(
                1,
                versions.get(1).getVersionNumber()
        );

        // STEP 5: Verify document points to latest version
        DocumentEntity updated =
                documentRepository.findById(documentId)
                        .orElseThrow();

        assertEquals(
                secondVersion.getStorageKey(),
                updated.getStorageKey()
        );

        // STEP 6: Download version 1
        byte[] firstDownload =
                versionService.downloadVersion(documentId, 1)
                        .orElseThrow()
                        .getInputStream()
                        .readAllBytes();

        assertArrayEquals(
                firstFile.getBytes(),
                firstDownload
        );

        // STEP 7: Download version 2
        byte[] secondDownload =
                versionService.downloadVersion(documentId, 2)
                        .orElseThrow()
                        .getInputStream()
                        .readAllBytes();

        assertArrayEquals(
                secondFile.getBytes(),
                secondDownload
        );

        // STEP 8: Save storage keys before deletion
        String firstKey =
                versions.get(1).getStorageKey();

        String secondKey =
                versions.get(0).getStorageKey();

        // Verify both physical files exist before deletion
        assertTrue(
                storageService.load(firstKey).exists()
        );

        assertTrue(
                storageService.load(secondKey).exists()
        );

        // STEP 9: Delete document
        assertTrue(
                documentService.deleteDocument(documentId)
        );

        // STEP 10: Verify parent document deleted from PostgreSQL
        assertFalse(
                documentRepository.existsById(documentId)
        );

        // STEP 11: Verify all versions deleted from PostgreSQL
        assertTrue(
                versionRepository
                        .findByDocumentIdOrderByVersionNumberDesc(documentId)
                        .isEmpty()
        );

        // STEP 12: Verify version 1 physical file deleted
        IOException firstFileError = assertThrows(
                IOException.class,
                () -> storageService.load(firstKey)
        );

        assertEquals(
                "Stored file not found",
                firstFileError.getMessage()
        );

        // STEP 13: Verify version 2 physical file deleted
        IOException secondFileError = assertThrows(
                IOException.class,
                () -> storageService.load(secondKey)
        );

        assertEquals(
                "Stored file not found",
                secondFileError.getMessage()
        );
    }
}
