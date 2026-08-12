package com.renovar.canteiro.io.platform.company;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(CompanyOnboardingApiIntegrationTest.AccountActivationEmailTestConfiguration.class)
class CompanyOnboardingApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanPriceRepository planPriceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private CompanyOnboardingPlanSelectionRepository selectionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void createsCompanyAndPendingOwnerOnlyAfterSelectingAnActivePricedPlan() throws Exception {
        Plan plan = planRepository.save(Plan.create("ONBOARDING_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(), "Onboarding", null));
        planPriceRepository.save(PlanPrice.create(plan.getId(), new BigDecimal("149.90"), LocalDate.now().minusDays(1), null));
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(post("/api/v1/onboarding/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"corporateName":"Construtora Onboarding","document":"%s","email":"company-%s@example.com","ownerEmail":"%s","planIds":["%s"]}
                                """.formatted(document(), UUID.randomUUID(), ownerEmail, plan.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerEmail").value(ownerEmail))
                .andExpect(jsonPath("$.quotedAmount").value(149.90))
                .andExpect(jsonPath("$.selectedPlanIds[0]").value(plan.getId().toString()))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        UUID companyId = UUID.fromString(response.replaceAll(".*\\\"companyId\\\":\\\"([^\\\"]+)\\\".*", "$1"));
        UUID ownerUserId = UUID.fromString(response.replaceAll(".*\\\"ownerUserId\\\":\\\"([^\\\"]+)\\\".*", "$1"));

        assertTrue(userRepository.findByEmail(ownerEmail).isPresent());
        assertTrue(companyUserRepository.findByUserIdAndCompanyId(ownerUserId, companyId).isPresent());
        assertEquals(1, selectionRepository.findByCompanyId(companyId).size());
        assertEquals(plan.getId(), selectionRepository.findByCompanyId(companyId).getFirst().getPlanId());
        assertTrue(auditEventRepository.findByCompanyId(companyId, Pageable.unpaged()).stream()
                .anyMatch(event -> event.getEntityId().equals(companyId) && event.getActorUserId().equals(ownerUserId)));
    }

    @Test
    void rejectsOnboardingWithoutAPlanBeforeCreatingTheOwnerUser() throws Exception {
        String ownerEmail = "missing-plan-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/onboarding/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"corporateName":"Construtora Sem Plano","document":"%s","email":"company-%s@example.com","ownerEmail":"%s","planIds":[]}
                                """.formatted(document(), UUID.randomUUID(), ownerEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertTrue(userRepository.findByEmail(ownerEmail).isEmpty());
    }

    private String document() {
        return "DOC-" + UUID.randomUUID().toString().substring(0, 14);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AccountActivationEmailTestConfiguration {

        @Bean
        @Primary
        AccountActivationEmailSender accountActivationEmailSender() {
            return (email, rawActivationToken, expiresAt) -> { };
        }
    }
}
