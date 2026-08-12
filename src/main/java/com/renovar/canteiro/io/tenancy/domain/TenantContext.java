package com.renovar.canteiro.io.tenancy.domain;

import java.util.UUID;

public record TenantContext(UUID userId, UUID companyId) {

    public TenantContext {
        if (userId == null || companyId == null) {
            throw new IllegalArgumentException("Tenant context requires a user and company");
        }
    }
}
