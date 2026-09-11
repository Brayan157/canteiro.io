package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.platform.company.api.response.PlatformCompanyDetailsResponse;
import com.renovar.canteiro.io.platform.company.api.response.PlatformCompanySummaryResponse;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.CompanySubscriptionAccessService;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItemRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformCompanyQueryService {

    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionItemRepository subscriptionItemRepository;
    private final CompanySubscriptionAccessService companySubscriptionAccessService;

    @Transactional(readOnly = true)
    public Page<PlatformCompanySummaryResponse> findCompanies(Pageable pageable) {
        requireOwner();
        return companyRepository.findAll(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PlatformCompanyDetailsResponse findCompany(UUID companyId) {
        requireOwner();
        Company company = companyRepository.findById(companyId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Company was not found")
        );
        return new PlatformCompanyDetailsResponse(
                company.getId(), company.getCorporateName(), company.getTradeName(), company.getDocument(),
                company.getEmail(), company.getPhone(), company.getAddress(), company.getLogo(), company.isActive(),
                company.getCreatedAt(),
                company.getUpdatedAt(),
                companySubscriptionAccessService.resolveAccessLevel(companyId),
                subscriptionRepository.findByCompanyId(companyId).stream()
                        .sorted(Comparator.comparing(
                                Subscription::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                        .map(subscription -> toSubscriptionResponse(subscription))
                        .toList()
        );
    }

    private PlatformCompanyDetailsResponse.SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return new PlatformCompanyDetailsResponse.SubscriptionResponse(
                subscription.getId(), subscription.getStatus(), subscription.getQuotedAmount(),
                subscription.getPricingSource(), subscription.getPlanBundleId(), subscription.getPricingEffectiveDate(),
                subscription.getTrialStartedOn(), subscription.getTrialEndsOn(), subscription.getCreatedAt(),
                subscriptionItemRepository.findBySubscriptionId(subscription.getId()).stream()
                        .map(item -> new PlatformCompanyDetailsResponse.SubscriptionItemResponse(
                                item.getPlanId(), item.getPlanCode(), item.getPlanName()
                        ))
                        .toList()
        );
    }

    private PlatformCompanySummaryResponse toSummary(Company company) {
        Subscription subscription = subscriptionRepository.findByCompanyId(company.getId()).stream()
                .max(Comparator.comparing(Subscription::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        return new PlatformCompanySummaryResponse(
                company.getId(), company.getCorporateName(), company.getTradeName(), company.getDocument(),
                company.getEmail(), company.isActive(), subscription == null ? null : subscription.getId(),
                subscription == null ? null : subscription.getStatus(),
                companySubscriptionAccessService.resolveAccessLevel(company.getId()),
                subscription == null ? null : subscription.getTrialEndsOn()
        );
    }

    private void requireOwner() {
        PlatformOperatorContext operator = platformOperatorContextHolder.currentOperator()
                .orElseThrow(() -> new AccessDeniedException("A platform owner is required"));
        if (operator.globalRole() != PlatformUserRole.PLATFORM_OWNER) {
            throw new AccessDeniedException("A platform owner is required");
        }
    }
}
