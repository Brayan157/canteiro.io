package com.renovar.canteiro.io.shared.infrastructure.storage;

public interface StorageProvider {

    void store(String storageKey, byte[] content);

    void delete(String storageKey);
}
