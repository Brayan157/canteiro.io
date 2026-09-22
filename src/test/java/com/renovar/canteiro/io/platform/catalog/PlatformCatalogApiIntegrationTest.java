package com.renovar.canteiro.io.platform.catalog;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlatformCatalogApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void platformOwnerManagesPlansFeaturesAndBundles() throws Exception {
        User owner = createPlatformUser("catalog-owner@example.com", PlatformUserRole.PLATFORM_OWNER);
        String planId = createPlan(owner, "STARTER_API", "Starter");
        String featureId = createFeature(owner, "CUSTOMERS_API");

        mockMvc.perform(post("/api/v1/platform/catalog/plans/{planId}/features", planId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featureId\":\"" + featureId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(planId))
                .andExpect(jsonPath("$.childId").value(featureId));

        mockMvc.perform(post("/api/v1/platform/catalog/plans/{planId}/prices", planId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"99.90\",\"validFrom\":\"2026-08-12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogItemId").value(planId))
                .andExpect(jsonPath("$.amount").value("99.90"));

        String bundleId = createBundle(owner, "STARTER_PROMO_API", "Starter promotion");
        mockMvc.perform(post("/api/v1/platform/catalog/bundles/{bundleId}/plans", bundleId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + planId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(bundleId))
                .andExpect(jsonPath("$.childId").value(planId));

        mockMvc.perform(post("/api/v1/platform/catalog/bundles/{bundleId}/prices", bundleId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"89.90\",\"validFrom\":\"2026-08-12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogItemId").value(bundleId))
                .andExpect(jsonPath("$.amount").value("89.90"));

        mockMvc.perform(get("/api/v1/platform/catalog/plans")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + planId + "')]").exists());
    }

    @Test
    void platformSupportCannotManageTheCatalog() throws Exception {
        User support = createPlatformUser("catalog-support@example.com", PlatformUserRole.PLATFORM_SUPPORT);

        mockMvc.perform(post("/api/v1/platform/catalog/plans")
                        .with(jwt().jwt(token -> token.subject(support.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DENIED\",\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void platformOwnerReadsPlanPriceHistoryWithoutChangingPreviousPeriods() throws Exception {
        User owner = createPlatformUser("plan-price-history-owner@example.com", PlatformUserRole.PLATFORM_OWNER);
        String planId = createPlan(owner, "PRICE_HISTORY_PLAN", "Price history plan");
        createPlanPrice(owner, planId, "89.90", "2025-01-01", "2025-12-31");
        createPlanPrice(owner, planId, "99.90", "2026-01-01", "2026-12-31");
        createPlanPrice(owner, planId, "109.90", "2027-01-01", null);

        mockMvc.perform(get("/api/v1/platform/catalog/plans/{planId}/prices", planId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].catalogItemId").value(planId))
                .andExpect(jsonPath("$[0].amount").value("109.90"))
                .andExpect(jsonPath("$[0].validFrom").value("2027-01-01"))
                .andExpect(jsonPath("$[1].amount").value("99.90"))
                .andExpect(jsonPath("$[1].validUntil").value("2026-12-31"))
                .andExpect(jsonPath("$[2].amount").value("89.90"))
                .andExpect(jsonPath("$[2].validUntil").value("2025-12-31"));
    }

    @Test
    void platformOwnerReadsBundlePriceHistoryInDescendingValidityOrder() throws Exception {
        User owner = createPlatformUser("bundle-price-history-owner@example.com", PlatformUserRole.PLATFORM_OWNER);
        String bundleId = createBundle(owner, "PRICE_HISTORY_BUNDLE", "Price history bundle");
        createBundlePrice(owner, bundleId, "79.90", "2025-01-01", "2025-12-31");
        createBundlePrice(owner, bundleId, "89.90", "2026-01-01", null);

        mockMvc.perform(get("/api/v1/platform/catalog/bundles/{bundleId}/prices", bundleId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].catalogItemId").value(bundleId))
                .andExpect(jsonPath("$[0].amount").value("89.90"))
                .andExpect(jsonPath("$[0].validFrom").value("2026-01-01"))
                .andExpect(jsonPath("$[1].amount").value("79.90"))
                .andExpect(jsonPath("$[1].validFrom").value("2025-01-01"));
    }

    @Test
    void platformSupportCannotReadPriceHistory() throws Exception {
        User owner = createPlatformUser("price-history-owner@example.com", PlatformUserRole.PLATFORM_OWNER);
        User support = createPlatformUser("price-history-support@example.com", PlatformUserRole.PLATFORM_SUPPORT);
        String planId = createPlan(owner, "PRICE_HISTORY_DENIED", "Denied history");

        mockMvc.perform(get("/api/v1/platform/catalog/plans/{planId}/prices", planId)
                        .with(jwt().jwt(token -> token.subject(support.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returnsNotFoundForMissingPriceHistoryCatalogItems() throws Exception {
        User owner = createPlatformUser("missing-price-history-owner@example.com", PlatformUserRole.PLATFORM_OWNER);

        mockMvc.perform(get("/api/v1/platform/catalog/plans/{planId}/prices", UUID.randomUUID())
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/platform/catalog/bundles/{bundleId}/prices", UUID.randomUUID())
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnAuthenticatedUserWithoutAPlatformContext() throws Exception {
        mockMvc.perform(get("/api/v1/platform/catalog/plans")
                        .with(jwt().jwt(token -> token.subject(java.util.UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String createPlan(User owner, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/catalog/plans")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createFeature(User owner, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/catalog/features")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"type\":\"MODULE\",\"name\":\"Customers\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private String createBundle(User owner, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/catalog/bundles")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void createPlanPrice(User owner, String planId, String amount, String validFrom, String validUntil) throws Exception {
        mockMvc.perform(post("/api/v1/platform/catalog/plans/{planId}/prices", planId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priceRequest(amount, validFrom, validUntil)))
                .andExpect(status().isCreated());
    }

    private void createBundlePrice(User owner, String bundleId, String amount, String validFrom, String validUntil) throws Exception {
        mockMvc.perform(post("/api/v1/platform/catalog/bundles/{bundleId}/prices", bundleId)
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priceRequest(amount, validFrom, validUntil)))
                .andExpect(status().isCreated());
    }

    private String priceRequest(String amount, String validFrom, String validUntil) {
        String validUntilField = validUntil == null ? "" : ",\"validUntil\":\"" + validUntil + "\"";
        return "{\"amount\":\"" + amount + "\",\"validFrom\":\"" + validFrom + "\"" + validUntilField + "}";
    }

    private User createPlatformUser(String email, PlatformUserRole globalRole) {
        User user = User.create(email, UserType.PLATFORM);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        platformUserRepository.save(PlatformUser.create(persistedUser.getId(), globalRole));
        return persistedUser;
    }
}
