package com.renovar.canteiro.io.tenancy.application;

import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantContextResolver {

    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;

    @Transactional(readOnly = true)
    public Optional<TenantContext> resolve(UUID authenticatedUserId) {
        var user = userRepository.findById(authenticatedUserId)
                .filter(currentUser -> currentUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new TenantAuthenticationException("Authenticated user is inactive or does not exist"));

        if (user.getUserType() == UserType.PLATFORM) {
            return Optional.empty();
        }

        return companyUserRepository.findByUserId(user.getId())
                .map(companyUser -> new TenantContext(user.getId(), companyUser.getCompanyId()))
                .or(() -> {
                    throw new TenantAuthenticationException("Company user does not have a company link");
                });
    }
}
