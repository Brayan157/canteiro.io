package com.renovar.canteiro.io.shared.domain;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class ProportionalMoneyAllocationTest {
    @Test void closesEveryCentWithoutExceedingTinyOriginCapacities() {
        Map<UUID,BigDecimal> weights=new HashMap<>();
        for(int i=0;i<100;i++)weights.put(new UUID(0,i+1),new BigDecimal("0.01"));
        var shares=ProportionalMoneyAllocation.allocate(new BigDecimal("0.99"),weights);
        assertEquals(new BigDecimal("0.99"),shares.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        shares.forEach((id,amount)->assertTrue(amount.signum()>=0&&amount.compareTo(weights.get(id))<=0));
        Map<UUID,BigDecimal> reversed=new LinkedHashMap<>();weights.entrySet().stream().sorted(Map.Entry.<UUID,BigDecimal>comparingByKey().reversed())
            .forEach(e->reversed.put(e.getKey(),e.getValue()));
        assertEquals(shares,ProportionalMoneyAllocation.allocate(new BigDecimal("0.99"),reversed));
    }
    @Test void rejectsNegativeCapacityAndOverAllocationAndAcceptsEmptyZero() {
        assertThrows(IllegalArgumentException.class,()->ProportionalMoneyAllocation.allocate(BigDecimal.ZERO,Map.of(UUID.randomUUID(),new BigDecimal("-1.00"))));
        assertThrows(IllegalArgumentException.class,()->ProportionalMoneyAllocation.allocate(new BigDecimal("1.01"),Map.of(UUID.randomUUID(),new BigDecimal("1.00"))));
        assertEquals(Map.of(),ProportionalMoneyAllocation.allocate(new BigDecimal("0.00"),Map.of()));
    }
}
