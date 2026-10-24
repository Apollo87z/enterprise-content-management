package com.ecm.enterprisecontentmanagementbackend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageDirectory;

    public FileStorageService(
            @Value("${app.storage.location}") String location
    ) throws IOException {
        this.storageDirectory = Path.of(location)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(storageDirectory);
    }

    public String store(MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file");
        }

        String storageKey = UUID.randomUUID().toString();
        Path destination = storageDirectory.resolve(storageKey);

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination);
        }

        return storageKey;
    }

    public Resource load(String storageKey) throws IOException {

        Path file = resolveStorageKey(storageKey);

        if (!Files.isRegularFile(file)) {
            throw new IOException("Stored file not found");
        }

        return new UrlResource(file.toUri());
    }

    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveStorageKey(storageKey));
    }

    private Path resolveStorageKey(String storageKey) {

        UUID.fromString(storageKey);

        Path file = storageDirectory
                .resolve(storageKey)
                .normalize();

        if (!file.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Invalid storage path");
        }

        return file;
    }
}
