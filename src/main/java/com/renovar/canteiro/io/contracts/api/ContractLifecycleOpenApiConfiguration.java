package com.renovar.canteiro.io.contracts.api;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContractLifecycleOpenApiConfiguration {
    @Bean
    OpenApiCustomizer closureAuthorizationNullableEnum() {
        return api -> {
            if (api.getComponents() == null || api.getComponents().getSchemas() == null) return;
            var response = api.getComponents().getSchemas().get("ClosureDecisionResponse");
            if (response == null || response.getProperties() == null) return;
            Object property = response.getProperties().get("authorizationMode");
            if (property instanceof io.swagger.v3.oas.models.media.Schema<?> mode && mode.getEnum() != null && !mode.getEnum().contains(null)) includeNull(mode);
        };
    }
    private static <T> void includeNull(io.swagger.v3.oas.models.media.Schema<T> schema) {
        var values = new java.util.ArrayList<T>(schema.getEnum());
        values.add(null); schema.setEnum(values);
    }
}
