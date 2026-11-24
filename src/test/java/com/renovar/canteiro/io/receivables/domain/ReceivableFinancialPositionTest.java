package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceivableFinancialPositionTest {
    @Test void separatesOpenPartialAndReceivedRegardlessOfStalePersistedStatus() {
        assertEquals(ReceivableFinancialPosition.Status.OPEN, position("0", "0", "0", ReceivableStatus.RECEIVED).status());
        assertEquals(ReceivableFinancialPosition.Status.PARTIALLY_RECEIVED, position("0", "25", "0", ReceivableStatus.OPEN).status());
        assertEquals(ReceivableFinancialPosition.Status.RECEIVED, position("0", "100", "0", ReceivableStatus.OPEN).status());
    }

    @Test void neverCallsAClosureByAdjustmentCashReceived() {
        for (var balance : new String[][] {{"100", "0", "0"}, {"0", "0", "100"}, {"10", "70", "20"}}) {
            var result = position(balance[0], balance[1], balance[2], ReceivableStatus.RECEIVED);
            assertEquals(ReceivableFinancialPosition.Status.SETTLED_WITH_ADJUSTMENTS, result.status());
            assertEquals(new BigDecimal("0.00"), result.outstandingAmount());
            assertEquals(new BigDecimal(balance[1]).setScale(2), result.confirmedSettlementAmount());
        }
    }

    @Test void preservesCancelledAndRenegotiatedHistoryWithoutCallingItCollectible() {
        assertEquals(ReceivableFinancialPosition.Status.CANCELLED, position("0", "0", "0", ReceivableStatus.CANCELLED).status());
        assertEquals(ReceivableFinancialPosition.Status.RENEGOTIATED, position("0", "0", "0", ReceivableStatus.RENEGOTIATED).status());
    }

    private ReceivableFinancialPosition position(String withheld, String received, String credited, ReceivableStatus persisted) {
        return ReceivableFinancialPosition.from(persisted, new ReceivableBalance(UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal(withheld), new BigDecimal(received), new BigDecimal(credited)));
    }
}
