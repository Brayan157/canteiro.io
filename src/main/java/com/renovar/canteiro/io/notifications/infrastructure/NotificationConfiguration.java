package com.renovar.canteiro.io.notifications.infrastructure;

import com.renovar.canteiro.io.notifications.application.NotificationDeliveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({NotificationEmailProperties.class, NotificationDeliveryProperties.class})
public class NotificationConfiguration {
}
