package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/** Temporary fail-closed policy until explicit invoice-item cash attribution is implemented. */
@Service
public class ReceivableSettlementCreationPolicy {
    public static final String INVOICE_LINK_REQUIRED = "SETTLEMENT_INVOICE_LINK_REQUIRED";

    public boolean isAvailable() {
        return false;
    }

    public String blockReason() {
        return INVOICE_LINK_REQUIRED;
    }

    public void requireAvailable() {
        // Deliberately no runtime bypass: generic invoice existence cannot establish exact coverage.
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION,
                "New receipts are temporarily unavailable until explicit invoice-item attribution is supported. Historical reads and reversals remain available.",
                List.of(), INVOICE_LINK_REQUIRED);
    }
}
