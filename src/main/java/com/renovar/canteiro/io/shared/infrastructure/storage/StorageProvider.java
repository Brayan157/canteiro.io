package com.renovar.canteiro.io.shared.infrastructure.storage;

public interface StorageProvider {

    void store(String storageKey, byte[] content);

    byte[] load(String storageKey);

    default java.io.InputStream openStream(String storageKey) {
        return new java.io.ByteArrayInputStream(load(storageKey));
    }

    void delete(String storageKey);
}
