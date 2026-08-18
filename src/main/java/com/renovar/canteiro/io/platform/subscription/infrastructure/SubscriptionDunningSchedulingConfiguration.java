package com.renovar.canteiro.io.platform.subscription.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(PaymentSynchronizationProperties.class)
public class SubscriptionDunningSchedulingConfiguration {
}
