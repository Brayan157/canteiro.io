package com.renovar.canteiro.io.identity.infrastructure.security;

import com.renovar.canteiro.io.identity.application.AccessTokenIssuer;
import com.renovar.canteiro.io.identity.application.AuthenticationProperties;
import com.renovar.canteiro.io.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final AuthenticationProperties authenticationProperties;
    private final Clock clock;

    @Override
    public String issue(User user) {
        Instant currentInstant = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authenticationProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(currentInstant)
                .expiresAt(currentInstant.plus(authenticationProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("user_type", user.getUserType().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
