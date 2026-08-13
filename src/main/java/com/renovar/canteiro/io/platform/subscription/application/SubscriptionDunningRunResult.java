package com.renovar.canteiro.io.platform.subscription.application;

public record SubscriptionDunningRunResult(
        int companiesEvaluated,
        int accessChanges,
        int noticesCreated
) {

    public SubscriptionDunningRunResult {
        if (companiesEvaluated < 0 || accessChanges < 0 || noticesCreated < 0) {
            throw new IllegalArgumentException("Subscription dunning result counts cannot be negative");
        }
    }

    public SubscriptionDunningRunResult plus(SubscriptionDunningRunResult other) {
        return new SubscriptionDunningRunResult(
                companiesEvaluated + other.companiesEvaluated,
                accessChanges + other.accessChanges,
                noticesCreated + other.noticesCreated
        );
    }

    public static SubscriptionDunningRunResult empty() {
        return new SubscriptionDunningRunResult(0, 0, 0);
    }
}
