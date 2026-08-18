package com.renovar.canteiro.io.platform.subscription.infrastructure.asaas;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "integrations.asaas", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AsaasSandboxProperties.class)
public class AsaasSandboxConfiguration {

    @Bean
    PaymentGateway asaasSandboxPaymentGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AsaasSandboxProperties properties
    ) {
        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader("access_token", properties.apiKey())
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
        return new AsaasSandboxPaymentGateway(restClient, objectMapper, properties);
    }
}
