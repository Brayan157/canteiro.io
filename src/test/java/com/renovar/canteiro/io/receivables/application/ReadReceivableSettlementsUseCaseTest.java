package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadReceivableSettlementsUseCaseTest {
    @Mock private TenantContextHolder tenants;
    @Mock private AccessAuthorizationService authorization;
    @Mock private ReceivableRepository receivables;
    @Mock private ReceivableSettlementRepository settlements;
    private final UUID company = UUID.randomUUID(), titleId = UUID.randomUUID(), contractId = UUID.randomUUID();
    private final ReceivableSettlementFilter filter = new ReceivableSettlementFilter(null, null, null, null, null);
    private ReadReceivableSettlementsUseCase service;

    @BeforeEach void setup() {
        service = new ReadReceivableSettlementsUseCase(tenants, authorization, receivables, settlements);
        when(tenants.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), company));
    }

    @Test void validatesScopedParentThenReadsOnlyExactTitleContractWithoutBillingOrBalanceCalls() {
        var title = mock(ReceivableReadProjection.class);
        when(title.id()).thenReturn(titleId); when(title.contractId()).thenReturn(contractId);
        when(receivables.findReadByIdAndCompanyId(titleId, company)).thenReturn(Optional.of(title));
        var page = PageRequest.of(0, 20);
        when(settlements.findHistory(company, titleId, contractId, filter, page)).thenReturn(Page.empty(page));
        assertEquals(0, service.find(titleId, filter, page).getTotalElements());
        verify(authorization).requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        verify(receivables).findReadByIdAndCompanyId(titleId, company);
        verify(settlements).findHistory(company, titleId, contractId, filter, page);
        verifyNoMoreInteractions(authorization, receivables, settlements);
    }

    @Test void deniesBeforeAccessingAnyParentOrCashHistory() {
        doThrow(new AccessDeniedException("denied")).when(authorization).requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        assertThrows(AccessDeniedException.class, () -> service.find(titleId, filter, PageRequest.of(0, 20)));
        verifyNoInteractions(receivables, settlements);
    }

    @Test void rejectsMissingOrForeignParentBeforeHistoryAndCount() {
        when(receivables.findReadByIdAndCompanyId(titleId, company)).thenReturn(Optional.empty());
        assertThrows(TenantResourceNotFoundException.class, () -> service.find(titleId, filter, PageRequest.of(0, 20)));
        verifyNoInteractions(settlements);
    }

    @Test void rejectsOversizeOffsetBeforeQuerying() {
        assertThrows(ApiException.class, () -> service.find(titleId, filter, PageRequest.of(Integer.MAX_VALUE, 100)));
        verifyNoInteractions(receivables, settlements);
    }
}
