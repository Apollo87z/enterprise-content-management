package com.ecm.enterprisecontentmanagementbackend.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocumentFileValidator {

    private static final String PDF = "application/pdf";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    public void validate(MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds 10 MB");
        }

        String contentType = file.getContentType();

        if (!PDF.equals(contentType) && !DOCX.equals(contentType)) {
            throw new IllegalArgumentException(
                    "Only PDF and DOCX files are supported"
            );
        }

        byte[] bytes = file.getBytes();

        if (PDF.equals(contentType) && !isPdf(bytes)) {
            throw new IllegalArgumentException("Invalid PDF file");
        }

        if (DOCX.equals(contentType) && !isDocx(bytes)) {
            throw new IllegalArgumentException("Invalid DOCX file");
        }
    }

    private boolean isPdf(byte[] bytes) {

        if (bytes.length < 10) {
            return false;
        }

        String header = new String(
                bytes, 0, Math.min(bytes.length, 8),
                StandardCharsets.US_ASCII
        );

        if (!header.startsWith("%PDF-")) {
            return false;
        }

        String tail = new String(
                bytes,
                Math.max(0, bytes.length - 1024),
                Math.min(1024, bytes.length),
                StandardCharsets.US_ASCII
        );

        return tail.contains("%%EOF");
    }

    private boolean isDocx(byte[] bytes) throws IOException {

        if (bytes.length < 4 ||
                bytes[0] != 'P' ||
                bytes[1] != 'K') {
            return false;
        }

        Set<String> required = Set.of(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/document.xml"
        );

        java.util.Set<String> found = new java.util.HashSet<>();

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(bytes))) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                if (required.contains(entry.getName())) {
                    found.add(entry.getName());
                }

                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException exception) {
            return false;
        }

        return found.containsAll(required);
    }
}
