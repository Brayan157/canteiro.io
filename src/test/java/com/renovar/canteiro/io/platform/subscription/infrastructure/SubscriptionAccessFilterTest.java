package com.renovar.canteiro.io.platform.subscription.infrastructure;

import com.renovar.canteiro.io.platform.subscription.application.CompanySubscriptionAccessService;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionAccessFilterTest {

    @Test
    void ignoresOnboardingRoutesWithoutResolvingTheCompanyAccess() throws Exception {
        TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
        CompanySubscriptionAccessService accessService = mock(CompanySubscriptionAccessService.class);
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(
                tenantContextHolder, accessService, mock(tools.jackson.databind.ObjectMapper.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/onboarding/companies");
        AtomicBoolean endpointReached = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> endpointReached.set(true));

        assertTrue(endpointReached.get());
        verify(tenantContextHolder, never()).requireCurrentTenant();
        verify(accessService, never()).resolveAccessLevel(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksTheExactCompanyRootRouteWhenSubscriptionAccessIsBlocked() throws Exception {
        UUID companyId = UUID.randomUUID();
        TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
        CompanySubscriptionAccessService accessService = mock(CompanySubscriptionAccessService.class);
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(accessService.resolveAccessLevel(companyId)).thenReturn(SubscriptionAccessLevel.BLOCKED);
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(
                tenantContextHolder, accessService, mock(tools.jackson.databind.ObjectMapper.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/company");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("A blocked company request must not reach the endpoint");
        });

        assertEquals(403, response.getStatus());
        verify(accessService).resolveAccessLevel(companyId);
    }
}
