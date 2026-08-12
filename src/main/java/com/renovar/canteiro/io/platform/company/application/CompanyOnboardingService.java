package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.access.application.InitialCompanyOwnerAccess;
import com.renovar.canteiro.io.access.application.InitialCompanyOwnerAccessProvisioner;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import com.renovar.canteiro.io.identity.application.ActivationTokenGenerator;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.catalog.application.CatalogPricingService;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelection;
import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelectionRepository;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyOnboardingService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final CompanyOnboardingPlanSelectionRepository selectionRepository;
    private final CatalogPricingService catalogPricingService;
    private final AccountActivationTokenRepository accountActivationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final AccountActivationEmailSender accountActivationEmailSender;
    private final AccountActivationProperties accountActivationProperties;
    private final InitialCompanyOwnerAccessProvisioner initialCompanyOwnerAccessProvisioner;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional
    public CompanyOnboardingResult onboard(CompanyOnboardingCommand command) {
        String ownerEmail = command.ownerEmail().trim();
        String document = command.document().trim();
        String companyEmail = command.email().trim();
        if (userRepository.findByEmail(ownerEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "Email is already in use");
        }
        if (companyRepository.findByDocument(document).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "Company document is already in use");
        }
        if (companyRepository.findByEmail(companyEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "Company email is already in use");
        }

        LocalDate pricingDate = LocalDate.now(clock);
        CatalogPriceQuote priceQuote;
        try {
            priceQuote = catalogPricingService.quote(command.planIds(), pricingDate);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, exception.getMessage());
        }

        Company company = companyRepository.save(Company.create(
                command.corporateName().trim(), command.tradeName(), document, companyEmail,
                command.phone(), command.address(), command.logo()
        ));
        User owner = userRepository.save(User.create(ownerEmail, UserType.COMPANY));
        companyUserRepository.save(CompanyUser.create(owner.getId(), company.getId()));
        InitialCompanyOwnerAccess initialOwnerAccess = initialCompanyOwnerAccessProvisioner.provision(
                company.getId(), owner.getId()
        );

        Instant selectedAt = clock.instant();
        List<java.util.UUID> selectedPlanIds = priceQuote.planIds().stream().sorted().toList();
        selectedPlanIds.forEach(planId -> selectionRepository.save(
                CompanyOnboardingPlanSelection.create(company.getId(), planId, selectedAt)
        ));
        createActivationAndSendEmail(owner);
        auditEventRecorder.recordInitialCompanyOnboarding(company.getId(), owner.getId(), Map.of(
                "document", company.getDocument(),
                "ownerEmail", owner.getEmail(),
                "selectedPlanIds", selectedPlanIds,
                "quotedAmount", priceQuote.amount(),
                "pricingSource", priceQuote.source().name(),
                "planBundleId", priceQuote.planBundleId() == null ? "" : priceQuote.planBundleId().toString(),
                "initialOwnerRoleId", initialOwnerAccess.roleId().toString(),
                "initialOwnerRoleName", initialOwnerAccess.roleName(),
                "initialOwnerPermissionCodes", initialOwnerAccess.permissionCodes()
        ));
        return new CompanyOnboardingResult(company.getId(), owner.getId(), owner.getEmail(), selectedPlanIds, priceQuote);
    }

    private void createActivationAndSendEmail(User user) {
        String rawActivationToken = activationTokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(accountActivationProperties.tokenTtl());
        accountActivationTokenRepository.save(AccountActivationToken.create(
                user.getId(), activationTokenHasher.hash(rawActivationToken), expiresAt
        ));
        accountActivationEmailSender.send(user.getEmail(), rawActivationToken, expiresAt);
    }
}
