package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.access.application.InitialCompanyOwnerAccess;
import com.renovar.canteiro.io.access.application.InitialCompanyOwnerAccessProvisioner;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import com.renovar.canteiro.io.identity.application.ActivationTokenGenerator;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.catalog.application.CatalogPricingService;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelection;
import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelectionRepository;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.SubscriptionSnapshotService;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyOnboardingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyUserRepository companyUserRepository;
    @Mock
    private CompanyOnboardingPlanSelectionRepository selectionRepository;
    @Mock
    private CatalogPricingService catalogPricingService;
    @Mock
    private AccountActivationTokenRepository accountActivationTokenRepository;
    @Mock
    private ActivationTokenGenerator activationTokenGenerator;
    @Mock
    private ActivationTokenHasher activationTokenHasher;
    @Mock
    private AccountActivationEmailSender accountActivationEmailSender;
    @Mock
    private InitialCompanyOwnerAccessProvisioner initialCompanyOwnerAccessProvisioner;
    @Mock
    private SubscriptionSnapshotService subscriptionSnapshotService;
    @Mock
    private AuditEventRecorder auditEventRecorder;

    private final AccountActivationProperties accountActivationProperties = new AccountActivationProperties(Duration.ofHours(24), null);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CompanyOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new CompanyOnboardingService(
                companyRepository, userRepository, companyUserRepository, selectionRepository, catalogPricingService,
                accountActivationTokenRepository, activationTokenGenerator, activationTokenHasher,
                accountActivationEmailSender, accountActivationProperties, initialCompanyOwnerAccessProvisioner,
                subscriptionSnapshotService, auditEventRecorder, clock
        );
    }

    @Test
    void recordsEverySelectedPlanAndCreatesThePendingInitialOwner() {
        UUID companyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Company company = Company.rehydrate(
                companyId, "Construtora", null, "123", "company@example.com", null, null, null, true, NOW, NOW
        );
        User owner = User.rehydrate(
                ownerId, "owner@example.com", UserType.COMPANY, UserStatus.PENDING_ACTIVATION,
                null, null, null, NOW, NOW
        );
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(companyRepository.findByDocument("123")).thenReturn(Optional.empty());
        when(companyRepository.findByEmail("company@example.com")).thenReturn(Optional.empty());
        when(catalogPricingService.quote(eq(List.of(planId)), eq(LocalDate.of(2026, 8, 12))))
                .thenReturn(new CatalogPriceQuote(
                        Set.of(planId), new BigDecimal("99.90"), LocalDate.of(2026, 8, 12),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                ));
        when(companyRepository.save(any())).thenReturn(company);
        when(userRepository.save(any())).thenReturn(owner);
        when(activationTokenGenerator.generate()).thenReturn("activation-token");
        when(activationTokenHasher.hash("activation-token")).thenReturn("token-hash");
        when(initialCompanyOwnerAccessProvisioner.provision(companyId, ownerId)).thenReturn(
                new InitialCompanyOwnerAccess(UUID.randomUUID(), "Company Administrator", List.of("USERS.MANAGE_USERS"))
        );

        CompanyOnboardingResult result = service.onboard(command(planId));

        assertEquals(companyId, result.companyId());
        assertEquals(ownerId, result.ownerUserId());
        ArgumentCaptor<CompanyOnboardingPlanSelection> selection = ArgumentCaptor.forClass(CompanyOnboardingPlanSelection.class);
        verify(selectionRepository).save(selection.capture());
        assertEquals(companyId, selection.getValue().getCompanyId());
        assertEquals(planId, selection.getValue().getPlanId());
        assertEquals(NOW, selection.getValue().getSelectedAt());
        verify(companyUserRepository).save(any());
        verify(initialCompanyOwnerAccessProvisioner).provision(companyId, ownerId);
        verify(subscriptionSnapshotService).createInitialSubscription(companyId, result.priceQuote());
        verify(accountActivationEmailSender).send(eq("owner@example.com"), eq("activation-token"), any());
        verify(auditEventRecorder).recordInitialCompanyOnboarding(eq(companyId), eq(ownerId), any());
    }

    @Test
    void doesNotCreateAnyCompanyDataWhenPlanSelectionIsInvalid() {
        UUID planId = UUID.randomUUID();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(companyRepository.findByDocument("123")).thenReturn(Optional.empty());
        when(companyRepository.findByEmail("company@example.com")).thenReturn(Optional.empty());
        when(catalogPricingService.quote(eq(List.of(planId)), any())).thenThrow(new IllegalArgumentException("Requested plan is inactive"));

        ApiException exception = assertThrows(ApiException.class, () -> service.onboard(command(planId)));

        assertEquals(400, exception.getStatus().value());
        assertTrue(exception.getMessage().contains("inactive"));
        verify(companyRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(selectionRepository, never()).save(any());
        verify(subscriptionSnapshotService, never()).createInitialSubscription(any(), any());
    }

    private CompanyOnboardingCommand command(UUID planId) {
        return new CompanyOnboardingCommand(
                "Construtora", null, "123", "company@example.com", null, null, null,
                "owner@example.com", List.of(planId)
        );
    }
}
