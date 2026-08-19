package com.renovar.canteiro.io.shared.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class LocalStorageProvider implements StorageProvider {

    private final Path basePath;

    LocalStorageProvider(LocalStorageProperties properties) {
        if (properties.basePath() == null || properties.basePath().isBlank()) {
            throw new IllegalArgumentException("A local storage base path is required");
        }
        this.basePath = Path.of(properties.basePath()).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store attachment", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete attachment", exception);
        }
    }

    private Path resolve(String storageKey) {
        Path target = basePath.resolve(storageKey).normalize();
        if (!target.startsWith(basePath)) {
            throw new IllegalArgumentException("Storage key resolves outside the configured storage path");
        }
        return target;
    }
}
