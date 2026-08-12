package com.renovar.canteiro.io.platform.subscription;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.SubscriptionSnapshotService;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItemRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionSnapshotPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionSnapshotService subscriptionSnapshotService;

    @Autowired
    private SubscriptionItemRepository subscriptionItemRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndProtectsTheSubscriptionPriceAndCompositionSnapshot() {
        Company company = companyRepository.save(Company.create(
                "Construtora Snapshot", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "snapshot-" + UUID.randomUUID() + "@example.com",
                null, null, null
        ));
        Plan plan = planRepository.save(Plan.create(
                "SNAPSHOT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                "Plano original", null
        ));
        Subscription subscription = subscriptionSnapshotService.createInitialSubscription(
                company.getId(),
                new CatalogPriceQuote(
                        Set.of(plan.getId()), new BigDecimal("199.90"), LocalDate.of(2026, 8, 12),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        );
        var item = subscriptionItemRepository.findBySubscriptionId(subscription.getId()).getFirst();

        assertEquals(new BigDecimal("199.90"), subscription.getQuotedAmount());
        assertEquals("Plano original", item.getPlanName());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE subscription SET quoted_amount = ? WHERE id = ?", new BigDecimal("1.00"), subscription.getId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE subscription_item SET plan_name = ? WHERE id = ?", "Plano alterado", item.getId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM subscription_item WHERE id = ?", item.getId()
        ));
    }

    @Test
    void persistsTheTrialLifecycleWithoutChangingThePriceSnapshot() {
        Company company = companyRepository.save(Company.create(
                "Construtora Trial", null, "DOC-" + UUID.randomUUID().toString().substring(0, 16),
                "lifecycle-" + UUID.randomUUID() + "@example.com", null, null, null
        ));
        Subscription subscription = subscriptionRepository.save(Subscription.create(
                company.getId(), new CatalogPriceQuote(
                        Set.of(UUID.randomUUID()), new BigDecimal("219.90"), LocalDate.of(2026, 8, 12),
                        CatalogPricingSource.INDIVIDUAL_PLANS, null
                )
        ));

        subscription.startTrial(LocalDate.of(2026, 8, 12));
        Subscription trialSubscription = subscriptionRepository.save(subscription);
        trialSubscription.advanceTrial(LocalDate.of(2026, 9, 11));
        Subscription awaitingPaymentSubscription = subscriptionRepository.save(trialSubscription);

        assertEquals(SubscriptionStatus.AWAITING_PAYMENT, awaitingPaymentSubscription.getStatus());
        assertEquals(LocalDate.of(2026, 8, 12), awaitingPaymentSubscription.getTrialStartedOn());
        assertEquals(LocalDate.of(2026, 9, 11), awaitingPaymentSubscription.getTrialEndsOn());
        assertEquals(new BigDecimal("219.90"), awaitingPaymentSubscription.getQuotedAmount());
    }
}
