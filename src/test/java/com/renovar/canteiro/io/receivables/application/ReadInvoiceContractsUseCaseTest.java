package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.InvoiceContractSummary;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadInvoiceContractsUseCaseTest {
    @Mock private TenantContextHolder tenants;
    @Mock private AccessAuthorizationService authorization;
    @Mock private InvoiceRepository invoices;
    private final UUID company = UUID.randomUUID();
    private ReadInvoiceContractsUseCase service;

    @BeforeEach void setup() {
        service = new ReadInvoiceContractsUseCase(tenants, authorization, invoices);
        when(tenants.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), company));
    }

    @Test void normalizesSearchAndRequiresOnlyInvoiceReadWithTenant() {
        var page = PageRequest.of(0, 20);
        when(invoices.findContractsWithInvoices(company, "Usina", page)).thenReturn(Page.empty(page));
        assertEquals(0, service.find(" Usina ", page).getTotalElements());
        verify(authorization).requirePermission(AccessModule.INVOICES, AccessAction.READ);
        verify(invoices).findContractsWithInvoices(company, "Usina", page);
        verifyNoMoreInteractions(authorization, invoices);
    }

    @Test void unauthorizedNeverQueriesEitherRead() {
        doThrow(new AccessDeniedException("denied")).when(authorization).requirePermission(AccessModule.INVOICES, AccessAction.READ);
        assertThrows(AccessDeniedException.class, () -> service.find(null, PageRequest.of(0, 20)));
        assertThrows(AccessDeniedException.class, () -> service.get(UUID.randomUUID()));
        verifyNoInteractions(invoices);
    }

    @Test void invalidSearchAndOffsetNeverQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.find("x".repeat(201), PageRequest.of(0, 20)));
        assertThrows(ApiException.class, () -> service.find(null, PageRequest.of(Integer.MAX_VALUE, 100)));
        verifyNoInteractions(invoices);
    }

    @Test void exactContextDoesNotRequireBrowserSearchAndForeignOrMissingIsNotFound() {
        UUID id = UUID.randomUUID();
        var expected = new InvoiceContractSummary(id, "Usina", null, "0001/2027");
        when(invoices.findContractWithInvoicesByIdAndCompanyId(id, company)).thenReturn(Optional.of(expected));
        assertEquals(expected, service.get(id));
        UUID missing = UUID.randomUUID();
        when(invoices.findContractWithInvoicesByIdAndCompanyId(missing, company)).thenReturn(Optional.empty());
        assertThrows(TenantResourceNotFoundException.class, () -> service.get(missing));
    }
}
