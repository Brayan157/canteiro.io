package com.renovar.canteiro.io.platform.subscription;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.application.SubscriptionDunningService;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TrustUnlockApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");
    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlatformChargeRepository platformChargeRepository;

    @Autowired
    private SubscriptionDunningService subscriptionDunningService;

    @Autowired
    private CompanySubscriptionAccessRepository companySubscriptionAccessRepository;

    @Autowired
    private TrustUnlockRepository trustUnlockRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void ownerGrantsTrustUnlockRestoresAccessAndRecordsItsAuthor() throws Exception {
        User owner = createPlatformUser(PlatformUserRole.PLATFORM_OWNER);
        PlatformCharge charge = overdueCharge();
        subscriptionDunningService.evaluateCompany(charge.getCompanyId());
        assertEquals(SubscriptionAccessLevel.BLOCKED, companySubscriptionAccessRepository
                .findByCompanyId(charge.getCompanyId())
                .orElseThrow()
                .getAccessLevel());
        Instant expiresAt = Instant.now().plusSeconds(86_400);

        mockMvc.perform(post("/api/v1/platform/subscriptions/charges/{chargeId}/trust-unlocks", charge.getId())
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"reason\":\"Customer committed to paying this week\","
                                + "\"expiresAt\":\"" + expiresAt + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(charge.getCompanyId().toString()))
                .andExpect(jsonPath("$.chargeId").value(charge.getId().toString()))
                .andExpect(jsonPath("$.grantedByUserId").value(owner.getId().toString()));

        assertEquals(1, trustUnlockRepository.countByChargeId(charge.getId()));
        assertEquals(SubscriptionAccessLevel.FULL, companySubscriptionAccessRepository
                .findByCompanyId(charge.getCompanyId())
                .orElseThrow()
                .getAccessLevel());
        assertTrue(auditEventRepository.findByCompanyId(charge.getCompanyId(), PageRequest.of(0, 20))
                .getContent()
                .stream()
                .anyMatch(event -> event.getEntityType().equals("TrustUnlock")
                        && event.getActorType() == AuditActorType.PLATFORM_USER
                        && event.getActorUserId().equals(owner.getId())));
    }

    @Test
    void rejectsTheThirdTrustUnlockForTheSameCharge() throws Exception {
        User owner = createPlatformUser(PlatformUserRole.PLATFORM_OWNER);
        PlatformCharge charge = overdueCharge();

        grant(owner, charge, "First agreement", Instant.now().plusSeconds(86_400)).andExpect(status().isCreated());
        grant(owner, charge, "Second agreement", Instant.now().plusSeconds(172_800)).andExpect(status().isCreated());
        grant(owner, charge, "Third agreement", Instant.now().plusSeconds(259_200))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        assertEquals(2, trustUnlockRepository.countByChargeId(charge.getId()));
    }

    @Test
    void platformSupportCannotGrantTrustUnlock() throws Exception {
        User support = createPlatformUser(PlatformUserRole.PLATFORM_SUPPORT);
        PlatformCharge charge = overdueCharge();

        grant(support, charge, "Support cannot grant this", Instant.now().plusSeconds(86_400))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertEquals(0, trustUnlockRepository.countByChargeId(charge.getId()));
    }

    private org.springframework.test.web.servlet.ResultActions grant(
            User user,
            PlatformCharge charge,
            String reason,
            Instant expiresAt
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/platform/subscriptions/charges/{chargeId}/trust-unlocks", charge.getId())
                .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"reason\":\"" + reason + "\",\"expiresAt\":\"" + expiresAt + "\"}"));
    }

    private User createPlatformUser(PlatformUserRole globalRole) {
        User user = User.create("operator-" + UUID.randomUUID() + "@example.com", UserType.PLATFORM);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        platformUserRepository.save(PlatformUser.create(persistedUser.getId(), globalRole));
        return persistedUser;
    }

    private PlatformCharge overdueCharge() {
        Company company = companyRepository.save(Company.create(
                "Construtora Trust Ltda.", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "trust-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
        Subscription subscription = subscriptionRepository.save(Subscription.create(
                company.getId(),
                new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.now(ZoneOffset.UTC),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));
        return platformChargeRepository.save(PlatformCharge.create(
                company.getId(), subscription.getId(), PROVIDER, "trust-key-" + UUID.randomUUID(),
                "cus_123", "pay_" + UUID.randomUUID(), PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.now(ZoneOffset.UTC).minusDays(12), PlatformChargeStatus.PENDING
        ));
    }
}
