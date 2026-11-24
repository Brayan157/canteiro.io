package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableFilter(String search, String reference, String contractSearch, String workSearch,
        String customerName, String billingReference, UUID contractId, UUID workId, UUID finalCustomerId,
        ReceivableStatus status, CurrencyCode currency, LocalDate dueFrom, LocalDate dueTo,
        ReceivableFinancialView financialView) {
    public ReceivableFilter(String search, String reference, String contractSearch, String workSearch,
            String customerName, String billingReference, UUID contractId, UUID workId, UUID finalCustomerId,
            ReceivableStatus status, CurrencyCode currency, LocalDate dueFrom, LocalDate dueTo) {
        this(search, reference, contractSearch, workSearch, customerName, billingReference, contractId, workId,
                finalCustomerId, status, currency, dueFrom, dueTo, ReceivableFinancialView.ALL);
    }

    public ReceivableFilter {
        financialView = financialView == null ? ReceivableFinancialView.ALL : financialView;
        search = text(search, 200);
        reference = text(reference, 100);
        contractSearch = text(contractSearch, 200);
        workSearch = text(workSearch, 200);
        customerName = text(customerName, 200);
        billingReference = text(billingReference, 100);
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new IllegalArgumentException("Receivable due date range is invalid");
        }
    }

    private static String text(String value, int limit) {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized != null && normalized.length() > limit) {
            throw new IllegalArgumentException("Receivable filter exceeds its maximum length of " + limit);
        }
        return normalized;
    }
}
