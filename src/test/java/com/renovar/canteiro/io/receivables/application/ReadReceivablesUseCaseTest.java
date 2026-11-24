package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.ReceivableFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
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
class ReadReceivablesUseCaseTest {
    @Mock private TenantContextHolder tenants;
    @Mock private AccessAuthorizationService authorization;
    @Mock private ReceivableRepository repository;
    private final UUID company = UUID.randomUUID();
    private final ReceivableFilter filter = new ReceivableFilter(null, null, null, null, null, null, null, null, null, null, null, null, null);
    private ReadReceivablesUseCase service;

    @BeforeEach void setup() {
        service = new ReadReceivablesUseCase(tenants, authorization, repository);
        when(tenants.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), company));
    }

    @Test void requiresOnlyReceivablesReadAndScopesEveryRead() {
        var page = PageRequest.of(0, 20);
        when(repository.findByCompanyId(company, filter, page)).thenReturn(Page.empty(page));
        assertEquals(0, service.find(filter, page).getTotalElements());
        verify(authorization).requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        verify(repository).findByCompanyId(company, filter, page);
        verifyNoMoreInteractions(authorization, repository);
    }

    @Test void rejectsUnauthorizedBeforeRepositoryAccess() {
        doThrow(new AccessDeniedException("denied")).when(authorization).requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        assertThrows(AccessDeniedException.class, () -> service.get(UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, () -> service.find(filter, PageRequest.of(0, 20)));
        verifyNoInteractions(repository);
    }

    @Test void rejectsOversizeOffsetWithoutQuerying() {
        assertThrows(ApiException.class, () -> service.find(filter, PageRequest.of(Integer.MAX_VALUE, 100)));
        verifyNoInteractions(repository);
    }

    @Test void missingOrForeignTitleHasSameSafeNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findReadByIdAndCompanyId(id, company)).thenReturn(Optional.empty());
        assertThrows(TenantResourceNotFoundException.class, () -> service.get(id));
        verify(repository).findReadByIdAndCompanyId(id, company);
        verifyNoMoreInteractions(repository);
    }
}
