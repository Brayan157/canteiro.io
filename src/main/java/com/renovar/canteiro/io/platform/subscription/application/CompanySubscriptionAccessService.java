package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanySubscriptionAccessService {

    private final CompanySubscriptionAccessRepository companySubscriptionAccessRepository;

    @Transactional(readOnly = true)
    public SubscriptionAccessLevel resolveAccessLevel(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company subscription access requires a company");
        }
        return companySubscriptionAccessRepository.findByCompanyId(companyId)
                .map(access -> access.getAccessLevel())
                .orElse(SubscriptionAccessLevel.FULL);
    }
}
