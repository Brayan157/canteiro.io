package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformOperatorContextResolver {

    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;

    @Transactional(readOnly = true)
    public Optional<PlatformOperatorContext> resolve(UUID authenticatedUserId) {
        var user = userRepository.findById(authenticatedUserId)
                .filter(currentUser -> currentUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new PlatformAuthenticationException("Authenticated user is inactive or does not exist"));
        if (user.getUserType() != UserType.PLATFORM) {
            return Optional.empty();
        }
        return platformUserRepository.findByUserId(user.getId())
                .map(platformUser -> new PlatformOperatorContext(
                        user.getId(),
                        platformUser.getId(),
                        platformUser.getGlobalRole()
                ))
                .or(() -> {
                    throw new PlatformAuthenticationException("Platform user does not have a global role link");
                });
    }
}
