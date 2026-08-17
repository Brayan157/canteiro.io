package com.renovar.canteiro.io.platform.catalog.application;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItemRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogPricingServiceTest {

    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 8, 22);

    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanPriceRepository planPriceRepository;
    @Mock
    private PlanBundleRepository planBundleRepository;
    @Mock
    private PlanBundleItemRepository planBundleItemRepository;
    @Mock
    private PlanBundlePriceRepository planBundlePriceRepository;

    @Test
    void sumsIndividualPricesWhenSelectionIsASupersetOfAPromotionalBundle() {
        Plan first = plan("FIRST");
        Plan second = plan("SECOND");
        Plan third = plan("THIRD");
        PlanBundle bundle = bundle("FIRST_SECOND");
        mockActivePlans(first, second, third);
        when(planBundleRepository.findAllActive()).thenReturn(List.of(bundle));
        when(planBundleItemRepository.findActivePlanIdsByPlanBundleId(bundle.getId()))
                .thenReturn(Set.of(first.getId(), second.getId()));
        when(planPriceRepository.requirePriceEffectiveOn(first.getId(), PRICE_DATE))
                .thenReturn(price(first, "10.00"));
        when(planPriceRepository.requirePriceEffectiveOn(second.getId(), PRICE_DATE))
                .thenReturn(price(second, "20.00"));
        when(planPriceRepository.requirePriceEffectiveOn(third.getId(), PRICE_DATE))
                .thenReturn(price(third, "30.00"));

        CatalogPriceQuote quote = service().quote(List.of(first.getId(), second.getId(), third.getId()), PRICE_DATE);

        assertEquals(CatalogPricingSource.INDIVIDUAL_PLANS, quote.source());
        assertEquals(new BigDecimal("60.00"), quote.amount());
    }

    @Test
    void rejectsAmbiguousPromotionalBundlesForTheSameExactComposition() {
        Plan first = plan("AMBIGUOUS_FIRST");
        Plan second = plan("AMBIGUOUS_SECOND");
        PlanBundle firstBundle = bundle("AMBIGUOUS_ONE");
        PlanBundle secondBundle = bundle("AMBIGUOUS_TWO");
        mockActivePlans(first, second);
        when(planBundleRepository.findAllActive()).thenReturn(List.of(firstBundle, secondBundle));
        when(planBundleItemRepository.findActivePlanIdsByPlanBundleId(firstBundle.getId()))
                .thenReturn(Set.of(first.getId(), second.getId()));
        when(planBundleItemRepository.findActivePlanIdsByPlanBundleId(secondBundle.getId()))
                .thenReturn(Set.of(first.getId(), second.getId()));
        when(planBundlePriceRepository.findPriceEffectiveOn(firstBundle.getId(), PRICE_DATE))
                .thenReturn(Optional.of(bundlePrice(firstBundle, "25.00")));
        when(planBundlePriceRepository.findPriceEffectiveOn(secondBundle.getId(), PRICE_DATE))
                .thenReturn(Optional.of(bundlePrice(secondBundle, "24.00")));

        assertThrows(IllegalStateException.class, () -> service().quote(
                List.of(first.getId(), second.getId()), PRICE_DATE
        ));
    }

    private CatalogPricingService service() {
        return new CatalogPricingService(
                planRepository,
                planPriceRepository,
                planBundleRepository,
                planBundleItemRepository,
                planBundlePriceRepository
        );
    }

    private void mockActivePlans(Plan... plans) {
        for (Plan plan : plans) {
            when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        }
    }

    private Plan plan(String code) {
        return Plan.rehydrate(UUID.randomUUID(), code, code, null, true, null, null);
    }

    private PlanBundle bundle(String code) {
        return PlanBundle.rehydrate(UUID.randomUUID(), code, code, null, true, null, null);
    }

    private PlanPrice price(Plan plan, String amount) {
        return PlanPrice.rehydrate(
                UUID.randomUUID(), plan.getId(), new BigDecimal(amount), PRICE_DATE, null, null, null
        );
    }

    private PlanBundlePrice bundlePrice(PlanBundle bundle, String amount) {
        return PlanBundlePrice.rehydrate(
                UUID.randomUUID(), bundle.getId(), new BigDecimal(amount), PRICE_DATE, null, null, null
        );
    }
}
