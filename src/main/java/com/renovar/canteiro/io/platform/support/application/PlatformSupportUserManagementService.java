package com.renovar.canteiro.io.platform.support.application;

import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import com.renovar.canteiro.io.identity.application.ActivationTokenGenerator;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformSupportUserManagementService {

    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final AuditEventRecorder auditEventRecorder;
    private final UserRepository userRepository;
    private final PlatformUserRepository platformUserRepository;
    private final AccountActivationTokenRepository accountActivationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final AccountActivationEmailSender accountActivationEmailSender;
    private final AccountActivationProperties accountActivationProperties;
    private final Clock clock;

    @Transactional
    public PlatformSupportUser createSupportUser(CreatePlatformSupportUserCommand command) {
        requirePlatformOwner();
        String email = command.email().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "Email is already in use");
        }
        User user = userRepository.save(User.create(email, UserType.PLATFORM));
        PlatformUser platformUser = platformUserRepository.save(PlatformUser.create(user.getId(), PlatformUserRole.PLATFORM_SUPPORT));
        createActivationAndSendEmail(user);
        auditEventRecorder.recordDirectAction(
                AuditModule.PLATFORM,
                AuditAction.CREATE,
                "PlatformUser",
                platformUser.getId(),
                null,
                Map.of(
                        "email", user.getEmail(),
                        "globalRole", platformUser.getGlobalRole().name(),
                        "status", user.getStatus().name()
                ),
                Map.of()
        );
        return new PlatformSupportUser(user, platformUser);
    }

    private void requirePlatformOwner() {
        PlatformOperatorContext operator = platformOperatorContextHolder.currentOperator()
                .orElseThrow(() -> new AccessDeniedException("A platform owner is required"));
        if (operator.globalRole() != PlatformUserRole.PLATFORM_OWNER) {
            throw new AccessDeniedException("A platform owner is required");
        }
    }

    private void createActivationAndSendEmail(User user) {
        String rawActivationToken = activationTokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(accountActivationProperties.tokenTtl());
        accountActivationTokenRepository.save(AccountActivationToken.create(
                user.getId(),
                activationTokenHasher.hash(rawActivationToken),
                expiresAt
        ));
        accountActivationEmailSender.send(user.getEmail(), rawActivationToken, expiresAt);
    }
}
