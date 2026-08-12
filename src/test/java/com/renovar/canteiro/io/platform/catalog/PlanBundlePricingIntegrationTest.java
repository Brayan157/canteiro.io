package com.renovar.canteiro.io.platform.catalog;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.platform.catalog.application.CatalogPricingService;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanBundlePricingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 8, 11);

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

    @Autowired
    private CatalogPricingService catalogPricingService;

    @Test
    void appliesBundlePriceOnlyWhenTheSelectedPlansExactlyMatchItsComposition() {
        Plan starter = createPlanWithPrice("STARTER_BUNDLE", "99.90");
        Plan professional = createPlanWithPrice("PROFESSIONAL_BUNDLE", "199.90");
        Plan reporting = createPlanWithPrice("REPORTING_BUNDLE", "49.90");
        PlanBundle bundle = createBundle(starter, professional, "STARTER_PROFESSIONAL_PROMO", "249.90");

        CatalogPriceQuote bundleQuote = catalogPricingService.quote(List.of(starter.getId(), professional.getId()), PRICE_DATE);
        CatalogPriceQuote individualQuote = catalogPricingService.quote(
                List.of(starter.getId(), professional.getId(), reporting.getId()),
                PRICE_DATE
        );

        assertEquals(CatalogPricingSource.PLAN_BUNDLE, bundleQuote.source());
        assertEquals(bundle.getId(), bundleQuote.planBundleId());
        assertEquals(new BigDecimal("249.90"), bundleQuote.amount());
        assertEquals(CatalogPricingSource.INDIVIDUAL_PLANS, individualQuote.source());
        assertEquals(new BigDecimal("349.70"), individualQuote.amount());
    }

    @Test
    void preventsDuplicatedBundleItemsAndOverlappingBundlePrices() {
        Plan starter = createPlanWithPrice("STARTER_CONSTRAINT", "99.90");
        Plan professional = createPlanWithPrice("PROFESSIONAL_CONSTRAINT", "199.90");
        PlanBundle bundle = planBundleRepository.save(PlanBundle.create(
                "STARTER_PROFESSIONAL_CONSTRAINT",
                "Starter + Professional",
                null
        ));
        planBundleItemRepository.save(PlanBundleItem.create(bundle.getId(), starter.getId()));
        planBundlePriceRepository.save(PlanBundlePrice.create(
                bundle.getId(),
                new BigDecimal("249.90"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));

        assertThrows(DataIntegrityViolationException.class, () ->
                planBundleItemRepository.save(PlanBundleItem.create(bundle.getId(), starter.getId())));
        assertThrows(DataIntegrityViolationException.class, () -> planBundlePriceRepository.save(PlanBundlePrice.create(
                bundle.getId(),
                new BigDecimal("239.90"),
                LocalDate.of(2026, 6, 1),
                null
        )));
        planBundleItemRepository.save(PlanBundleItem.create(bundle.getId(), professional.getId()));
    }

    private Plan createPlanWithPrice(String code, String amount) {
        Plan plan = planRepository.save(Plan.create(code, code + " plan", null));
        planPriceRepository.save(PlanPrice.create(plan.getId(), new BigDecimal(amount), PRICE_DATE, null));
        return plan;
    }

    private PlanBundle createBundle(Plan firstPlan, Plan secondPlan, String code, String amount) {
        PlanBundle bundle = planBundleRepository.save(PlanBundle.create(code, code + " bundle", null));
        planBundleItemRepository.save(PlanBundleItem.create(bundle.getId(), firstPlan.getId()));
        planBundleItemRepository.save(PlanBundleItem.create(bundle.getId(), secondPlan.getId()));
        planBundlePriceRepository.save(PlanBundlePrice.create(bundle.getId(), new BigDecimal(amount), PRICE_DATE, null));
        return bundle;
    }
}
