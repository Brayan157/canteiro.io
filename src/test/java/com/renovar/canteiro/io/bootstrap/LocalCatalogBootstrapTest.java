package com.renovar.canteiro.io.bootstrap;

import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCatalogBootstrapTest {

    private static final UUID PLAN_ID = UUID.fromString("3ec76f3a-e7fc-423f-bf60-afbc1f4b7f91");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    @Mock
    private PlanRepository planRepository;
    @Mock
    private PlanPriceRepository planPriceRepository;

    private LocalCatalogBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new LocalCatalogBootstrap(
                planRepository,
                planPriceRepository,
                Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsAnActivePlanWithAPriceEffectiveTodayWhenLocalCatalogIsEmpty() throws Exception {
        Plan persistedPlan = Plan.rehydrate(PLAN_ID, "LOCAL_MVP", "MVP Local", null, true, null, null);
        when(planRepository.findByCode("LOCAL_MVP")).thenReturn(Optional.empty());
        when(planRepository.save(any(Plan.class))).thenReturn(persistedPlan);
        when(planPriceRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        ArgumentCaptor<PlanPrice> priceCaptor = ArgumentCaptor.forClass(PlanPrice.class);
        verify(planRepository).save(planCaptor.capture());
        verify(planPriceRepository).save(priceCaptor.capture());
        assertEquals("LOCAL_MVP", planCaptor.getValue().getCode());
        assertEquals("MVP Local", planCaptor.getValue().getName());
        assertEquals(PLAN_ID, priceCaptor.getValue().getPlanId());
        assertEquals(TODAY, priceCaptor.getValue().getValidFrom());
        assertEquals(null, priceCaptor.getValue().getValidUntil());
    }

    @Test
    void preservesAnExistingPlanWithAnEffectivePrice() throws Exception {
        Plan existingPlan = Plan.rehydrate(PLAN_ID, "LOCAL_MVP", "MVP Local", null, true, null, null);
        PlanPrice currentPrice = PlanPrice.rehydrate(
                UUID.randomUUID(), PLAN_ID, new BigDecimal("199.00"),
                TODAY.minusDays(1), null, null, null
        );
        when(planRepository.findByCode("LOCAL_MVP")).thenReturn(Optional.of(existingPlan));
        when(planPriceRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(currentPrice));

        bootstrap.run(new DefaultApplicationArguments());

        verify(planRepository, never()).save(any());
        verify(planPriceRepository, never()).save(any());
    }
}
