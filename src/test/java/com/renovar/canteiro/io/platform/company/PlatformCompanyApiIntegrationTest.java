package com.renovar.canteiro.io.platform.company;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
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
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItem;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItemRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlatformCompanyApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionItemRepository subscriptionItemRepository;

    @Test
    void listsAndDetailsCompaniesForThePlatformOwner() throws Exception {
        User owner = User.create("owner@platform.example", UserType.PLATFORM);
        owner.activate(passwordHasher.hash("Canteiro#2026Seguro"), Instant.now());
        owner = userRepository.save(owner);
        UUID ownerId = owner.getId();
        platformUserRepository.save(PlatformUser.create(ownerId, PlatformUserRole.PLATFORM_OWNER));
        Company company = companyRepository.save(Company.create(
                "Renovar Engenharia Ltda.", "Renovar", "12345678000199", "financeiro@renovar.example",
                null, null, null
        ));
        UUID planId = UUID.randomUUID();
        CatalogPriceQuote quote = new CatalogPriceQuote(
                Set.of(planId),
                new BigDecimal("199.90"),
                LocalDate.now(),
                CatalogPricingSource.INDIVIDUAL_PLANS,
                null
        );
        Subscription subscription = subscriptionRepository.save(Subscription.create(company.getId(), quote));
        subscriptionItemRepository.save(SubscriptionItem.create(subscription.getId(), planId, "PRO", "Professional"));

        mockMvc.perform(get("/api/v1/platform/companies")
                        .with(jwt().jwt(token -> token.subject(ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(company.getId().toString()))
                .andExpect(jsonPath("$.items[0].subscriptionId").value(subscription.getId().toString()))
                .andExpect(jsonPath("$.items[0].accessLevel").value("FULL"));

        mockMvc.perform(get("/api/v1/platform/companies/{companyId}", company.getId())
                        .with(jwt().jwt(token -> token.subject(ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(company.getId().toString()))
                .andExpect(jsonPath("$.subscriptions[0].id").value(subscription.getId().toString()))
                .andExpect(jsonPath("$.subscriptions[0].items[0].planCode").value("PRO"));
    }

    @Test
    void deniesPlatformSupportFromViewingCompanySubscriptions() throws Exception {
        User supportUser = User.create("support@platform.example", UserType.PLATFORM);
        supportUser.activate(passwordHasher.hash("Canteiro#2026Seguro"), Instant.now());
        supportUser = userRepository.save(supportUser);
        UUID supportUserId = supportUser.getId();
        platformUserRepository.save(PlatformUser.create(supportUserId, PlatformUserRole.PLATFORM_SUPPORT));

        mockMvc.perform(get("/api/v1/platform/companies")
                        .with(jwt().jwt(token -> token.subject(supportUserId.toString()))))
                .andExpect(status().isForbidden());
    }
}
