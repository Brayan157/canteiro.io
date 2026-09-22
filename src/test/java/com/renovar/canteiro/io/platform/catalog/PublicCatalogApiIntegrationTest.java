package com.renovar.canteiro.io.platform.catalog;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItem;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItemRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicCatalogApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanPriceRepository planPriceRepository;

    @Autowired
    private PlanBundleRepository planBundleRepository;

    @Autowired
    private PlanBundleItemRepository planBundleItemRepository;

    @Autowired
    private PlanBundlePriceRepository planBundlePriceRepository;

    @Test
    void exposesOnlyActiveCatalogItemsWithPricesEffectiveTodayWithoutAuthentication() throws Exception {
        LocalDate today = LocalDate.now();
        Plan plan = planRepository.save(Plan.create("ESSENTIAL", "Essential", "Base plan"));
        planPriceRepository.save(PlanPrice.create(plan.getId(), new BigDecimal("99.90"), today.minusDays(1), null));
        Plan unavailablePlan = planRepository.save(Plan.create("FUTURE", "Future", "Not yet available"));
        planPriceRepository.save(PlanPrice.create(
                unavailablePlan.getId(), new BigDecimal("149.90"), today.plusDays(1), null
        ));
        PlanBundle bundle = planBundleRepository.save(PlanBundle.create("ESSENTIAL_PLUS", "Essential Plus", "Bundle"));
        planBundleItemRepository.save(PlanBundleItem.create(bundle.getId(), plan.getId()));
        planBundlePriceRepository.save(PlanBundlePrice.create(
                bundle.getId(), new BigDecimal("89.90"), today.minusDays(1), null
        ));

        mockMvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveOn").value(today.toString()))
                .andExpect(jsonPath("$.plans.length()").value(1))
                .andExpect(jsonPath("$.plans[0].code").value("ESSENTIAL"))
                .andExpect(jsonPath("$.plans[0].amount").value(99.90))
                .andExpect(jsonPath("$.bundles.length()").value(1))
                .andExpect(jsonPath("$.bundles[0].code").value("ESSENTIAL_PLUS"))
                .andExpect(jsonPath("$.bundles[0].plans[0].id").value(plan.getId().toString()));
    }
}
