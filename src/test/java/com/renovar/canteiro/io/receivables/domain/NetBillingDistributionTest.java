package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetBillingDistributionTest {
    private BigDecimal money(String value) { return new BigDecimal(value); }
    @Test void distributesOnlyTheUncommittedNetWithDeterministicCentClosure() {
        var shares = NetBillingDistribution.allocate(money("0.02"), Map.of("A", money("0.01"), "B", money("0.01"), "C", money("0.01")));
        assertEquals(Map.of("A", money("0.00"), "B", money("0.01"), "C", money("0.01")), shares);
        assertEquals(Map.of("A", money("900.00"), "B", money("900.00")), NetBillingDistribution.allocate(money("1800.00"), Map.of("A", money("1000.00"), "B", money("1000.00"))));
        assertThrows(IllegalStateException.class, () -> NetBillingDistribution.allocate(money("2.01"), Map.of("A", money("2.00"))));
        assertThrows(IllegalArgumentException.class, () -> NetBillingDistribution.allocate(money("1.00"), Map.of("A", money("-2.00"))));
    }
    @Test void partialConsumptionFreezesItsBasisAndTheFinalCentConsumesTheRest() {
        BigDecimal first = NetBillingDistribution.consumeGross(money("44.99"), money("90.00"), money("100.00"));
        assertEquals(money("49.98"), first);
        BigDecimal last = NetBillingDistribution.consumeGross(money("45.01"), money("45.01"), money("100.00").subtract(first));
        assertEquals(money("100.00"), first.add(last));
        assertThrows(IllegalStateException.class, () -> NetBillingDistribution.consumeGross(money("90.01"), money("90.00"), money("100.00")));
        assertEquals(Map.of("A", money("0.00")), NetBillingDistribution.allocate(money("0.00"), Map.of("A", money("10.00"))));
    }
    @Test void preservesLegacyAndNetBasisThroughActivationAndCancellation() {
        UUID company=UUID.randomUUID(),installment=UUID.randomUUID(),contract=UUID.randomUUID(),service=UUID.randomUUID();
        var allocation=BillingAllocation.createNetService(company,installment,contract,service,null,money("90.00"),money("100.00"),money("10.00")).activate();
        assertEquals(money("90.00"),allocation.getAllocatedAmount());assertEquals(money("100.00"),allocation.cancel().getCapacityAmount());
        assertEquals(money("10.00"),allocation.cancel().getAppliedContractDiscountAmount());
        var legacy=BillingAllocation.createService(company,installment,contract,service,money("100.00"));
        assertNull(legacy.getNetBillingGrossAmount());assertEquals(money("100.00"),legacy.getCapacityAmount());
        assertThrows(IllegalArgumentException.class,()->BillingAllocation.createNetService(company,installment,contract,service,null,money("90.00"),money("100.00"),money("9.00")));
    }
}
