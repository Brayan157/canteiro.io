package com.renovar.canteiro.io.platform.catalog;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeature;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignment;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignmentRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureType;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanCatalogPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private PlanFeatureAssignmentRepository planFeatureAssignmentRepository;

    @Autowired
    private PlanPriceRepository planPriceRepository;

    @Test
    void persistsPlanFeaturesAndAPriceEffectiveForTheRequestedDate() {
        Plan plan = planRepository.save(Plan.create("STARTER", "Starter", "Initial platform plan"));
        PlanFeature feature = planFeatureRepository.save(PlanFeature.create(
                "CUSTOMERS",
                PlanFeatureType.MODULE,
                "Customers",
                "Customer management module"
        ));
        planFeatureAssignmentRepository.save(PlanFeatureAssignment.create(plan.getId(), feature.getId()));
        PlanPrice price = planPriceRepository.save(PlanPrice.create(
                plan.getId(),
                new BigDecimal("99.90"),
                LocalDate.of(2026, 8, 1),
                null
        ));

        assertEquals(plan.getId(), planRepository.findByCode("starter").orElseThrow().getId());
        assertEquals(feature.getId(), planFeatureRepository.findByCode("customers").orElseThrow().getId());
        assertEquals(1, planFeatureAssignmentRepository.findActiveByPlanId(plan.getId()).size());
        assertEquals(price.getId(), planPriceRepository.requirePriceEffectiveOn(
                plan.getId(),
                LocalDate.of(2026, 8, 15)
        ).getId());
    }

    @Test
    void preventsOverlappingPricePeriodsForTheSamePlan() {
        Plan plan = planRepository.save(Plan.create("PROFESSIONAL", "Professional", null));
        planPriceRepository.save(PlanPrice.create(
                plan.getId(),
                new BigDecimal("199.90"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));

        assertThrows(DataIntegrityViolationException.class, () -> planPriceRepository.save(PlanPrice.create(
                plan.getId(),
                new BigDecimal("249.90"),
                LocalDate.of(2026, 6, 1),
                null
        )));
    }

    @Test
    void allowsANewPriceAfterThePreviousPriceValidityIsClosed() {
        Plan plan = planRepository.save(Plan.create("BUSINESS", "Business", null));
        PlanPrice firstPrice = planPriceRepository.save(PlanPrice.create(
                plan.getId(),
                new BigDecimal("149.90"),
                LocalDate.of(2026, 1, 1),
                null
        ));
        firstPrice.endOn(LocalDate.of(2026, 8, 31));
        planPriceRepository.save(firstPrice);
        PlanPrice secondPrice = planPriceRepository.save(PlanPrice.create(
                plan.getId(),
                new BigDecimal("159.90"),
                LocalDate.of(2026, 9, 1),
                null
        ));

        assertEquals(secondPrice.getId(), planPriceRepository.requirePriceEffectiveOn(
                plan.getId(),
                LocalDate.of(2026, 9, 1)
        ).getId());
    }

    @Test
    void preventsDuplicateFeatureAssignmentForTheSamePlan() {
        Plan plan = planRepository.save(Plan.create("ENTERPRISE", "Enterprise", null));
        PlanFeature feature = planFeatureRepository.save(PlanFeature.create(
                "REPORTING",
                PlanFeatureType.FEATURE,
                "Report exports",
                null
        ));
        planFeatureAssignmentRepository.save(PlanFeatureAssignment.create(plan.getId(), feature.getId()));

        assertThrows(DataIntegrityViolationException.class, () ->
                planFeatureAssignmentRepository.save(PlanFeatureAssignment.create(plan.getId(), feature.getId())));
    }
}
