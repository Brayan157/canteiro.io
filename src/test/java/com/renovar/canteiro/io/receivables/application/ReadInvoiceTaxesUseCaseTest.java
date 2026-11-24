package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadInvoiceTaxesUseCaseTest {
    @Mock private TenantContextHolder tenants;
    @Mock private AccessAuthorizationService authorization;
    @Mock private InvoiceRepository invoices;
    @Mock private ContractRepository contracts;
    @Mock private TaxObligationRepository taxes;
    @Mock private ReceivableRepository receivables;
    private final UUID companyId = UUID.randomUUID(), invoiceId = UUID.randomUUID(), contractId = UUID.randomUUID(), receivableId = UUID.randomUUID();
    private ReadInvoiceTaxesUseCase service;

    @BeforeEach void setup() {
        service = new ReadInvoiceTaxesUseCase(tenants, authorization, invoices, contracts, taxes, receivables);
        when(tenants.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(invoices.findByIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(Invoice.rehydrate(invoiceId, companyId, contractId,
                "NF", null, null, LocalDate.of(2026, 10, 26), new BigDecimal("100.00"), InvoiceStatus.CANCELLED, Instant.now(), Instant.now())));
        when(contracts.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(Contract.rehydrate(contractId, companyId, UUID.randomUUID(),
                null, ContractPricingMode.FIXED, null, "Contrato", ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now(), CurrencyCode.EUR)));
    }

    @Test void rejectsUnexpectedForeignTaxEvenIfRepositoryReturnsIt() {
        page(TaxObligation.register(UUID.randomUUID(), invoiceId, contractId, null, "Info", null, null, BigDecimal.ONE, TaxTreatment.INFORMATIONAL));
        assertThrows(TenantResourceNotFoundException.class, () -> service.find(invoiceId, null, null, PageRequest.of(0, 20)));
        verifyNoInteractions(receivables);
    }

    @Test void rejectsMissingOrForeignLinkedReceivableWithoutRevealingIt() {
        page(withheld());
        when(receivables.findByIdAndCompanyId(receivableId, companyId)).thenReturn(Optional.empty());
        assertThrows(TenantResourceNotFoundException.class, () -> service.find(invoiceId, null, null, PageRequest.of(0, 20)));
    }

    @Test void rejectsLinkedReceivableFromAnotherContract() {
        page(withheld());
        when(receivables.findByIdAndCompanyId(receivableId, companyId)).thenReturn(Optional.of(title(UUID.randomUUID())));
        assertThrows(TenantResourceNotFoundException.class, () -> service.find(invoiceId, null, null, PageRequest.of(0, 20)));
    }

    @Test void acceptsCancelledLinkedHistoryWithoutDemandingTitlePermissionsOrRecalculatingItsAmount() {
        page(withheld().cancel());
        when(receivables.findByIdAndCompanyId(receivableId, companyId)).thenReturn(Optional.of(title(contractId).withStatus(ReceivableStatus.CANCELLED)));
        var result = service.find(invoiceId, null, null, PageRequest.of(0, 20)).getContent().getFirst();
        assertEquals("CANCELLED", result.tax().getStatus().name());
        assertEquals(new BigDecimal("10.00"), result.tax().getAmount()); assertEquals(CurrencyCode.EUR, result.currency());
        verify(authorization).requirePermission(AccessModule.INVOICES, AccessAction.READ);
        verifyNoMoreInteractions(authorization);
    }

    private TaxObligation withheld() {
        return TaxObligation.register(companyId, invoiceId, contractId, receivableId, "Retenção", null, null, new BigDecimal("10.00"), TaxTreatment.WITHHELD_BY_CUSTOMER);
    }
    private Receivable title(UUID contract) {
        return Receivable.create(companyId, contract, UUID.randomUUID(), UUID.randomUUID(), "Título", null, null, LocalDate.of(2026, 11, 25), new BigDecimal("100.00"));
    }
    private void page(TaxObligation tax) {
        when(taxes.findPageByInvoiceIdAndCompanyIdAndContractId(eq(invoiceId), eq(companyId), eq(contractId), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(tax)));
    }
}
