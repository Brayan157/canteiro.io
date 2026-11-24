package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ContractBillingPdfGenerator {
    Result write(Data data, ContractBillingReportRepository source, OutputStream output) throws IOException;
    record Data(UUID companyId, UUID contractId, String issuer, String issuerDocument, byte[] logo, String customer,
                String work, String contract, String contractStatus, CurrencyCode currency, Instant generatedAt,
                ContractNetAmount commercial, BigDecimal invoicedAmount, BigDecimal unassignedInvoicedAmount,
                BigDecimal confirmedReceivedAmount, String contractNumber) { }
    record Result(long serviceRows, long invoiceRows, int pages) { }
}
