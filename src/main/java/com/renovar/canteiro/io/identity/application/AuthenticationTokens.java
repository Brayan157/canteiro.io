package com.renovar.canteiro.io.identity.application;

public record AuthenticationTokens(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
}
