package com.renovar.canteiro.io.bootstrap;

import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Profile("local")
@Configuration
@RequiredArgsConstructor
public class LocalCatalogBootstrap implements ApplicationRunner {

    static final String LOCAL_PLAN_CODE = "LOCAL_MVP";
    private static final BigDecimal LOCAL_PLAN_AMOUNT = new BigDecimal("199.00");

    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Plan plan = planRepository.findByCode(LOCAL_PLAN_CODE)
                .orElseGet(() -> planRepository.save(Plan.create(
                        LOCAL_PLAN_CODE,
                        "MVP Local",
                        "Plano local para desenvolvimento e onboarding"
                )));
        LocalDate today = LocalDate.now(clock);
        boolean hasEffectivePrice = planPriceRepository.findByPlanId(plan.getId()).stream()
                .anyMatch(price -> !price.getValidFrom().isAfter(today)
                        && (price.getValidUntil() == null || !price.getValidUntil().isBefore(today)));

        if (!hasEffectivePrice) {
            planPriceRepository.save(PlanPrice.create(plan.getId(), LOCAL_PLAN_AMOUNT, today, null));
        }
    }
}
