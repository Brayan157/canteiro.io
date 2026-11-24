package com.renovar.canteiro.io.receivables.api;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingControlOpenApiConfiguration {
    @Bean
    OpenApiCustomizer billingFinancialStatusNullableEnum() {
        return api -> {
            if (api.getComponents() == null || api.getComponents().getSchemas() == null) return;
            for (String name : java.util.List.of("BillingAllocationControlResponse", "BillingInstallmentSummaryResponse", "BillingInstallmentDetailsResponse")) {
                var response = api.getComponents().getSchemas().get(name);
                if (response == null || response.getProperties() == null) continue;
                Object property = response.getProperties().get("receivableFinancialStatus");
                // JSON Schema enum also constrains null, even when type explicitly includes null.
                if (property instanceof io.swagger.v3.oas.models.media.Schema<?> status
                        && status.getEnum() != null && !status.getEnum().contains(null)) {
                    includeNull(status);
                }
            }
        };
    }

    private static <T> void includeNull(io.swagger.v3.oas.models.media.Schema<T> schema) {
        var values = new java.util.ArrayList<T>(schema.getEnum());
        values.add(null);
        schema.setEnum(values);
    }
}
