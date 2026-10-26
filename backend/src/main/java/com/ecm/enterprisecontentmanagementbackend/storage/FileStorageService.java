package com.ecm.enterprisecontentmanagementbackend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path storageDirectory;
    public FileStorageService(@Value("${app.storage.location}") String location) throws IOException {
        storageDirectory = Path.of(location).toAbsolutePath().normalize();
        Files.createDirectories(storageDirectory);
    }
    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Cannot upload an empty file");
        try (InputStream in = file.getInputStream()) { return store(in); }
    }
    // Each WOPI save gets a new immutable storage key; previous versions are preserved.
    public String store(InputStream input) throws IOException {
        String key = UUID.randomUUID().toString();
        Path path = resolveStorageKey(key);
        try {
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
    }
    public Resource load(String storageKey) throws IOException {
        Path file = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(file)) throw new IOException("Stored file not found");
        return new UrlResource(file.toUri());
    }
    public void delete(String storageKey) throws IOException { Files.deleteIfExists(resolveStorageKey(storageKey)); }
    private Path resolveStorageKey(String key) {
        UUID.fromString(key);
        Path path = storageDirectory.resolve(key).normalize();
        if (!path.startsWith(storageDirectory)) throw new IllegalArgumentException("Invalid storage path");
        return path;
    }
}
