package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.List;
import java.util.UUID;

public interface SubscriptionItemRepository {

    SubscriptionItem save(SubscriptionItem subscriptionItem);

    List<SubscriptionItem> findBySubscriptionId(UUID subscriptionId);
}
