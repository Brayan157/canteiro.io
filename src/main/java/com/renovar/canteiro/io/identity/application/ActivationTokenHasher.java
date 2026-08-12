package com.renovar.canteiro.io.identity.application;

public interface ActivationTokenHasher {

    String hash(String rawActivationToken);
}
