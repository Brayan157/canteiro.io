package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InvoiceWithholdingCapacityTest {
    private final UUID invoice = UUID.randomUUID();
    private final UUID first = new UUID(0, 1);
    private final UUID second = new UUID(0, 2);

    @Test void allocatesTaxByGrossExactItemAndSubtractsOnlyItsHistoricalCash() {
        var result = calculate(row(first,"1000.00","100.00","100.00","20.00"), row(second,"1000.00","100.00","900.00","100.00"));
        assertNull(result.unavailableReason());
        assertEquals(new BigDecimal("10.00"), result.items().get(first).withheldAmount());
        assertEquals(new BigDecimal("90.00"), result.items().get(first).netAmount());
        assertEquals(new BigDecimal("70.00"), result.items().get(first).availableAmount());
        assertEquals(new BigDecimal("90.00"), result.items().get(second).withheldAmount());
        assertEquals(new BigDecimal("710.00"), result.items().get(second).availableAmount());
    }

    @Test void closesCentsDeterministicallyIndependentOfRowOrder() {
        var a = row(first,"3.00","0.01","1.00","0.00");
        var b = row(second,"3.00","0.01","2.00","0.00");
        var result = calculate(a,b);
        assertEquals(result,calculate(b,a));
        assertEquals(new BigDecimal("0.00"), result.items().get(first).withheldAmount());
        assertEquals(new BigDecimal("0.01"), result.items().get(second).withheldAmount());
        assertEquals(new BigDecimal("2.99"), result.items().values().stream().map(InvoiceWithholdingCapacity.Item::netAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Test void reproducesReportedAmountsWithoutRoundingOrDoubleWithholding() {
        var result = calculate(row(first,"790000.00","5000.00","87777.78","0.00"),row(second,"790000.00","5000.00","702222.22","0.00"));
        assertEquals(new BigDecimal("5000.00"), result.items().values().stream().map(InvoiceWithholdingCapacity.Item::withheldAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(new BigDecimal("785000.00"), result.items().values().stream().map(InvoiceWithholdingCapacity.Item::availableAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Test void refusesToReinterpretHistoricalCashAboveTheNewNetItem() {
        var result = calculate(row(first,"100.00","10.00","60.00","60.00"),row(second,"100.00","10.00","40.00","20.00"));
        assertEquals("SETTLEMENT_WITHHOLDING_HISTORY_REQUIRES_RECONCILIATION",result.unavailableReason());
        assertEquals(0,result.items().size());
    }

    @Test void refusesUnprovenScopeIncompleteBasisAndExcessWithholding() {
        var a = row(first,"100.00","10.00","60.00","0.00");
        var b = row(second,"100.00","10.00","40.00","0.00");
        assertEquals("SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION",calculate(a).unavailableReason());
        assertEquals("SETTLEMENT_WITHHOLDING_SCOPE_REQUIRES_RECONCILIATION",calculate(new InvoiceSettlementRepository.WithholdingBasis(invoice,first,a.invoiceAmount(),a.withheldAmount(),false,true,a.itemAmount(),a.itemReceivedAmount(),2),b).unavailableReason());
        assertEquals("SETTLEMENT_WITHHOLDING_TOO_MANY_ITEMS",calculate(new InvoiceSettlementRepository.WithholdingBasis(invoice,first,a.invoiceAmount(),a.withheldAmount(),true,true,a.itemAmount(),a.itemReceivedAmount(),101)).unavailableReason());
        assertEquals("SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION",calculate(row(first,"100.00","101.00","60.00","0.00"),row(second,"100.00","101.00","40.00","0.00")).unavailableReason());
    }

    private InvoiceWithholdingCapacity calculate(InvoiceSettlementRepository.WithholdingBasis... rows) {
        return InvoiceWithholdingCapacity.calculate(List.of(rows)).get(invoice);
    }
    private InvoiceSettlementRepository.WithholdingBasis row(UUID item,String gross,String tax,String amount,String received) {
        return new InvoiceSettlementRepository.WithholdingBasis(invoice,item,new BigDecimal(gross),new BigDecimal(tax),true,true,new BigDecimal(amount),new BigDecimal(received),2);
    }
}
