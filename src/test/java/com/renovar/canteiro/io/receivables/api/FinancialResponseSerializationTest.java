package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.response.BillingAllocationResponse;
import com.renovar.canteiro.io.receivables.api.response.InvoiceResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableBalanceResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableSettlementResponse;
import com.renovar.canteiro.io.receivables.api.response.TaxObligationResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialResponseSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesMoneyAsExactCanonicalStringsIncludingNegativeAdjustmentsAndLargeAmounts() {
        var allocation = new BillingAllocationResponse(null, null, null, null, new BigDecimal("-60"), null);
        var amount = mapper.valueToTree(allocation).path("allocatedAmount");
        assertTrue(amount.isString());
        assertEquals("-60.00", amount.asText());
        var invoice = new InvoiceResponse(null, null, null, null, null, null,
                new BigDecimal("9007199254740993.99"), null, List.of(), com.renovar.canteiro.io.shared.domain.CurrencyCode.USD);
        assertEquals("9007199254740993.99", mapper.valueToTree(invoice).path("totalAmount").asText());
        assertTrue(mapper.valueToTree(invoice).path("totalAmount").isString());
    }

    @Test
    void serializesBalancesReceiptsAndTaxesWithoutScientificNotationOrBinaryFloatingPoint() {
        var zero = BigDecimal.ZERO;
        var balance = new ReceivableBalanceResponse(null, new BigDecimal("1E+3"), zero, zero,
                new BigDecimal("1000"), new BigDecimal("0.01"), new BigDecimal("999.99"), null, com.renovar.canteiro.io.shared.domain.CurrencyCode.EUR);
        var tree = mapper.valueToTree(balance);
        for (String field : List.of("originalAmount", "withheldAmount", "creditedAmount", "netReceivableAmount",
                "confirmedSettlementAmount", "outstandingAmount")) assertTrue(tree.path(field).isString(), field);
        assertEquals("1000.00", tree.path("originalAmount").asText());
        assertEquals("0.00", tree.path("withheldAmount").asText());
        var receipt = new ReceivableSettlementResponse(null, null, null, null, new BigDecimal("1.2"), null, null, null, null, null, com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL);
        assertEquals("1.20", mapper.valueToTree(receipt).path("amount").asText());
        var tax = new TaxObligationResponse(null, null, null, null, null, null, null, new BigDecimal("0.01"), null, null, com.renovar.canteiro.io.shared.domain.CurrencyCode.USD);
        assertTrue(mapper.valueToTree(tax).path("amount").isString());
    }
}
