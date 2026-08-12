package com.renovar.canteiro.io.bootstrap;

import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Profile("local")
@Configuration
@ConditionalOnProperty(prefix = "bootstrap.platform-owner", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalPlatformOwnerProperties.class)
@RequiredArgsConstructor
public class LocalPlatformOwnerBootstrap implements ApplicationRunner {

    private final LocalPlatformOwnerProperties properties;
    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = requireValue(properties.email(), "bootstrap.platform-owner.email").trim();
        String password = requireValue(properties.password(), "bootstrap.platform-owner.password");

        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        User owner = User.create(email, UserType.PLATFORM);
        owner.activate(passwordHasher.hash(password), clock.instant());
        User persistedOwner = userRepository.save(owner);
        platformUserRepository.save(PlatformUser.create(persistedOwner.getId(), PlatformUserRole.PLATFORM_OWNER));
    }

    private String requireValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be set when local platform owner bootstrap is enabled");
        }
        return value;
    }
}
