package com.renovar.canteiro.io.platform.company.api.response;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlatformCompanyDetailsResponse(
        UUID id, String corporateName, String tradeName, String document, String email, String phone, String address,
        String logo, boolean active, Instant createdAt, Instant updatedAt, SubscriptionAccessLevel accessLevel,
        List<SubscriptionResponse> subscriptions
) {
    public record SubscriptionResponse(
            UUID id, SubscriptionStatus status, BigDecimal quotedAmount, CatalogPricingSource pricingSource,
            UUID planBundleId, LocalDate pricingEffectiveDate, LocalDate trialStartedOn, LocalDate trialEndsOn,
            Instant createdAt, List<SubscriptionItemResponse> items
    ) {
    }

    public record SubscriptionItemResponse(UUID planId, String planCode, String planName) {
    }
}
