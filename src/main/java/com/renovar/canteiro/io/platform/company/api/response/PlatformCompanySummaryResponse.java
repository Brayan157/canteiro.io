package com.renovar.canteiro.io.platform.company.api.response;

import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record PlatformCompanySummaryResponse(
        UUID id, String corporateName, String tradeName, String document, String email, boolean active,
        UUID subscriptionId, SubscriptionStatus subscriptionStatus, SubscriptionAccessLevel accessLevel,
        LocalDate trialEndsOn
) {
}
