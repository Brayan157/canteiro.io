package com.renovar.canteiro.io.identity.api.response;

public record AuthenticationTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds
) {
}
