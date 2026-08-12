package com.renovar.canteiro.io.platform.subscription.domain;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Subscription {

    private final UUID id;
    private final UUID companyId;
    private SubscriptionStatus status;
    private final BigDecimal quotedAmount;
    private final CatalogPricingSource pricingSource;
    private final UUID planBundleId;
    private final LocalDate pricingEffectiveDate;
    private LocalDate trialStartedOn;
    private LocalDate trialEndsOn;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Subscription(
            UUID id,
            UUID companyId,
            SubscriptionStatus status,
            BigDecimal quotedAmount,
            CatalogPricingSource pricingSource,
            UUID planBundleId,
            LocalDate pricingEffectiveDate,
            LocalDate trialStartedOn,
            LocalDate trialEndsOn,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.companyId = requireCompanyId(companyId);
        this.status = requireStatus(status);
        this.quotedAmount = requireQuotedAmount(quotedAmount);
        this.pricingSource = requirePricingSource(pricingSource);
        this.planBundleId = requirePlanBundle(pricingSource, planBundleId);
        this.pricingEffectiveDate = requirePricingEffectiveDate(pricingEffectiveDate);
        this.trialStartedOn = trialStartedOn;
        this.trialEndsOn = trialEndsOn;
        requireTrialDates(status, trialStartedOn, trialEndsOn);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Subscription create(UUID companyId, CatalogPriceQuote quote) {
        if (quote == null) {
            throw new IllegalArgumentException("A subscription requires a price quote");
        }
        return new Subscription(
                null,
                companyId,
                SubscriptionStatus.PENDING_ACTIVATION,
                quote.amount(),
                quote.source(),
                quote.planBundleId(),
                quote.effectiveDate(),
                null,
                null,
                null,
                null
        );
    }

    public static Subscription rehydrate(
            UUID id,
            UUID companyId,
            SubscriptionStatus status,
            BigDecimal quotedAmount,
            CatalogPricingSource pricingSource,
            UUID planBundleId,
            LocalDate pricingEffectiveDate,
            LocalDate trialStartedOn,
            LocalDate trialEndsOn,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Subscription(
                id,
                companyId,
                status,
                quotedAmount,
                pricingSource,
                planBundleId,
                pricingEffectiveDate,
                trialStartedOn,
                trialEndsOn,
                createdAt,
                updatedAt
        );
    }

    public boolean startTrial(LocalDate startedOn) {
        if (status != SubscriptionStatus.PENDING_ACTIVATION) {
            return false;
        }
        if (startedOn == null) {
            throw new IllegalArgumentException("A trial start date is required");
        }
        status = SubscriptionStatus.TRIAL;
        trialStartedOn = startedOn;
        trialEndsOn = startedOn.plusDays(30);
        return true;
    }

    public boolean advanceTrial(LocalDate currentDate) {
        if (currentDate == null) {
            throw new IllegalArgumentException("A subscription transition date is required");
        }
        if (status != SubscriptionStatus.TRIAL || currentDate.isBefore(trialEndsOn)) {
            return false;
        }
        status = SubscriptionStatus.AWAITING_PAYMENT;
        return true;
    }

    private static UUID requireCompanyId(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("A subscription company is required");
        }
        return companyId;
    }

    private static SubscriptionStatus requireStatus(SubscriptionStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("A subscription status is required");
        }
        return status;
    }

    private static BigDecimal requireQuotedAmount(BigDecimal quotedAmount) {
        if (quotedAmount == null || quotedAmount.signum() < 0) {
            throw new IllegalArgumentException("A subscription quoted amount must be greater than or equal to zero");
        }
        try {
            return quotedAmount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("A subscription quoted amount must have at most two decimal places", exception);
        }
    }

    private static CatalogPricingSource requirePricingSource(CatalogPricingSource pricingSource) {
        if (pricingSource == null) {
            throw new IllegalArgumentException("A subscription pricing source is required");
        }
        return pricingSource;
    }

    private static UUID requirePlanBundle(CatalogPricingSource pricingSource, UUID planBundleId) {
        if (pricingSource == CatalogPricingSource.PLAN_BUNDLE && planBundleId == null) {
            throw new IllegalArgumentException("A bundled subscription requires its plan bundle");
        }
        if (pricingSource == CatalogPricingSource.INDIVIDUAL_PLANS && planBundleId != null) {
            throw new IllegalArgumentException("An individual-plan subscription cannot identify a plan bundle");
        }
        return planBundleId;
    }

    private static LocalDate requirePricingEffectiveDate(LocalDate pricingEffectiveDate) {
        if (pricingEffectiveDate == null) {
            throw new IllegalArgumentException("A subscription pricing effective date is required");
        }
        return pricingEffectiveDate;
    }

    private static void requireTrialDates(
            SubscriptionStatus status,
            LocalDate trialStartedOn,
            LocalDate trialEndsOn
    ) {
        if (status == SubscriptionStatus.PENDING_ACTIVATION
                && (trialStartedOn != null || trialEndsOn != null)) {
            throw new IllegalArgumentException("A pending subscription cannot have trial dates");
        }
        if (status != SubscriptionStatus.PENDING_ACTIVATION
                && (trialStartedOn == null || trialEndsOn == null || !trialEndsOn.equals(trialStartedOn.plusDays(30)))) {
            throw new IllegalArgumentException("A trial subscription must retain its 30-day trial period");
        }
    }
}
