package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded projections of official records, never approval proposals. */
public interface ContractBillingReportRepository {
    BigDecimal serviceSubtotal(UUID company, UUID contract);
    BigDecimal invoicedAmount(UUID company, UUID contract);
    void validateInvoiceOrigins(UUID company, UUID contract);
    List<Column> invoicePage(UUID company, UUID contract, int offset, int limit, boolean history);
    List<ServiceRow> servicePage(UUID company, UUID contract, UUID after, int limit, ContractNetAmount official);
    Map<CellKey, BigDecimal> cells(UUID company, UUID contract, List<UUID> invoices, List<UUID> services);
    List<NoteRow> invoiceHistoryPage(UUID company, UUID contract, int offset, int limit);
    BigDecimal unassignedInvoicedAmount(UUID company, UUID contract);
    record Column(UUID id, String number, String series, LocalDate issuedOn, String status, BigDecimal amount) { }
    List<OpenCharge> openChargePage(UUID company, UUID contract, int page, int size);
    record OpenCharge(String reference, LocalDate dueOn, BigDecimal charged, BigDecimal received,
            BigDecimal outstanding, String status, Long invoiceCount) { }
    record ServiceRow(UUID id, String name, String description, String status, BigDecimal commercialAmount,
            BigDecimal invoicedAmount, BigDecimal commercialBalance) { }
    record CellKey(UUID serviceId, UUID invoiceId) { }
    record NoteRow(Column invoice, String installmentReference, Instant observedAt, BigDecimal unreceivedCashAmount) { }
}
