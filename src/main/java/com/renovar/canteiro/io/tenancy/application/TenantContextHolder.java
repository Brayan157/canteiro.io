package com.renovar.canteiro.io.tenancy.application;

import com.renovar.canteiro.io.tenancy.domain.TenantContext;

import java.util.Optional;

public interface TenantContextHolder {

    Optional<TenantContext> currentTenant();

    TenantContext requireCurrentTenant();

    void setCurrentTenant(TenantContext tenantContext);

    void clear();
}
