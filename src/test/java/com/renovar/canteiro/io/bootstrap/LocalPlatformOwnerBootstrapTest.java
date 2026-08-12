package com.renovar.canteiro.io.bootstrap;

import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalPlatformOwnerBootstrapTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private PasswordHasher passwordHasher;

    private LocalPlatformOwnerBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new LocalPlatformOwnerBootstrap(
                new LocalPlatformOwnerProperties(true, "owner@canteiro.local", "iO123456!2026"),
                userRepository,
                platformUserRepository,
                passwordHasher,
                Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsAnActivePlatformOwnerWhenTheEmailDoesNotExist() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(userRepository.findByEmail("owner@canteiro.local")).thenReturn(Optional.empty());
        when(passwordHasher.hash("iO123456!2026")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> User.rehydrate(
                ownerId,
                invocation.getArgument(0, User.class).getEmail(),
                UserType.PLATFORM,
                UserStatus.ACTIVE,
                "hashed-password",
                Instant.parse("2026-08-12T10:00:00Z"),
                Instant.parse("2026-08-12T10:00:00Z"),
                Instant.parse("2026-08-12T10:00:00Z"),
                Instant.parse("2026-08-12T10:00:00Z")
        ));

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<PlatformUser> platformUserCaptor = ArgumentCaptor.forClass(PlatformUser.class);
        verify(userRepository).save(userCaptor.capture());
        verify(platformUserRepository).save(platformUserCaptor.capture());
        assertEquals(UserType.PLATFORM, userCaptor.getValue().getUserType());
        assertEquals(UserStatus.ACTIVE, userCaptor.getValue().getStatus());
        assertEquals(ownerId, platformUserCaptor.getValue().getUserId());
        assertEquals(PlatformUserRole.PLATFORM_OWNER, platformUserCaptor.getValue().getGlobalRole());
    }

    @Test
    void doesNothingWhenTheEmailAlreadyExists() throws Exception {
        User existingUser = User.rehydrate(
                UUID.randomUUID(), "owner@canteiro.local", UserType.PLATFORM, UserStatus.ACTIVE,
                "existing-hash", Instant.now(), Instant.now(), Instant.now(), Instant.now()
        );
        when(userRepository.findByEmail("owner@canteiro.local")).thenReturn(Optional.of(existingUser));

        bootstrap.run(new DefaultApplicationArguments());

        verify(passwordHasher, never()).hash(any());
        verify(userRepository, never()).save(any());
        verify(platformUserRepository, never()).save(any());
    }
}
