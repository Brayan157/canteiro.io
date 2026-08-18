package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.UUID;

public record SubscriptionDunningAssessment(
        SubscriptionAccessLevel accessLevel,
        UUID restrictionChargeId
) {

    public SubscriptionDunningAssessment {
        if (accessLevel == null) {
            throw new IllegalArgumentException("Subscription dunning access level is required");
        }
        if (accessLevel == SubscriptionAccessLevel.FULL && restrictionChargeId != null) {
            throw new IllegalArgumentException("Full dunning assessment cannot identify a restriction charge");
        }
        if (accessLevel != SubscriptionAccessLevel.FULL && restrictionChargeId == null) {
            throw new IllegalArgumentException("Restricted dunning assessment requires a charge");
        }
    }

    public static SubscriptionDunningAssessment fullAccess() {
        return new SubscriptionDunningAssessment(SubscriptionAccessLevel.FULL, null);
    }
}
