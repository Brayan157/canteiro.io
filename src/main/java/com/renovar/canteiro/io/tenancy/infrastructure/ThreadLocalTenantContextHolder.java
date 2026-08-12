package com.renovar.canteiro.io.tenancy.infrastructure;

import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ThreadLocalTenantContextHolder implements TenantContextHolder {

    private final ThreadLocal<TenantContext> tenantContext = new ThreadLocal<>();

    @Override
    public Optional<TenantContext> currentTenant() {
        return Optional.ofNullable(tenantContext.get());
    }

    @Override
    public TenantContext requireCurrentTenant() {
        return currentTenant().orElseThrow(() -> new IllegalStateException("A company tenant context is required"));
    }

    @Override
    public void setCurrentTenant(TenantContext currentTenant) {
        tenantContext.set(currentTenant);
    }

    @Override
    public void clear() {
        tenantContext.remove();
    }
}
