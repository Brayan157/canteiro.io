package com.renovar.canteiro.io.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact proportional attribution with deterministic, capacity-bounded rounding residuals. */
public final class ProportionalMoneyAllocation {
    private ProportionalMoneyAllocation() { }
    public static Map<UUID, BigDecimal> allocate(BigDecimal amount, Map<UUID, BigDecimal> weights) {
        if(weights.values().stream().anyMatch(w->w==null||w.signum()<0||w.scale()>2))
            throw new IllegalArgumentException("Attribution requires non-negative canonical money weights");
        List<UUID> ids = weights.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList();
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() < 0 || amount.compareTo(total) > 0) throw new IllegalArgumentException("Attribution exceeds its positive origin capacity");
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal assigned = BigDecimal.ZERO.setScale(2);
        for (UUID id : ids) {
            BigDecimal share = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : weights.get(id).multiply(amount).divide(total, 2, RoundingMode.DOWN);
            result.put(id, share); assigned = assigned.add(share);
        }
        BigDecimal remaining = amount.subtract(assigned);
        for (int index = ids.size() - 1; index >= 0 && remaining.signum() > 0; index--) {
            UUID id = ids.get(index);
            BigDecimal available = weights.get(id).subtract(result.get(id)).setScale(2);
            BigDecimal extra = available.min(remaining);
            result.put(id, result.get(id).add(extra)); remaining = remaining.subtract(extra);
        }
        if (remaining.signum() != 0) throw new IllegalStateException("Money attribution did not close");
        return Collections.unmodifiableMap(result);
    }
}
