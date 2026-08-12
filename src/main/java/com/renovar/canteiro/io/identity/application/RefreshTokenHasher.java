package com.renovar.canteiro.io.identity.application;

public interface RefreshTokenHasher {

    String hash(String rawRefreshToken);
}
