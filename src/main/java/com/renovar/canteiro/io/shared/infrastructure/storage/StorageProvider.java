package com.renovar.canteiro.io.shared.infrastructure.storage;

public interface StorageProvider {

    void store(String storageKey, byte[] content);

    byte[] load(String storageKey);

    void delete(String storageKey);
}
