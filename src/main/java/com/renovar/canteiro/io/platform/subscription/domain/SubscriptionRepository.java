package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {

    Subscription save(Subscription subscription);

    Optional<Subscription> findById(UUID id);

    List<Subscription> findByCompanyId(UUID companyId);
}
