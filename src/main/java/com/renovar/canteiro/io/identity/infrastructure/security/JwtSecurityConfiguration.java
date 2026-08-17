package com.renovar.canteiro.io.identity.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.renovar.canteiro.io.identity.application.AuthenticationProperties;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import com.renovar.canteiro.io.identity.application.PasswordResetProperties;
import com.renovar.canteiro.io.platform.support.infrastructure.PlatformOperatorAuthenticationFilter;
import com.renovar.canteiro.io.platform.support.infrastructure.SupportTargetContextFilter;
import com.renovar.canteiro.io.platform.subscription.infrastructure.SubscriptionAccessFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.renovar.canteiro.io.tenancy.infrastructure.TenantContextAuthenticationFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({AuthenticationProperties.class, PasswordResetProperties.class, AccountActivationProperties.class})
public class JwtSecurityConfiguration {

    private final AuthenticationProperties authenticationProperties;
    private final TenantContextAuthenticationFilter tenantContextAuthenticationFilter;
    private final SubscriptionAccessFilter subscriptionAccessFilter;
    private final PlatformOperatorAuthenticationFilter platformOperatorAuthenticationFilter;
    private final SupportTargetContextFilter supportTargetContextFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/api/v1/onboarding/**", "/api/v1/webhooks/payments", "/swagger/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .addFilterAfter(tenantContextAuthenticationFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(subscriptionAccessFilter, TenantContextAuthenticationFilter.class)
                .addFilterAfter(platformOperatorAuthenticationFilter, TenantContextAuthenticationFilter.class)
                .addFilterAfter(supportTargetContextFilter, PlatformOperatorAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(authenticationProperties.issuer()));
        return jwtDecoder;
    }

    @Bean
    SecretKey jwtSecretKey() {
        return new SecretKeySpec(authenticationProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
