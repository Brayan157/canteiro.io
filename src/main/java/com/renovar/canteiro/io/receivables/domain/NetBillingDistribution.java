package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Proportional net capacities. Existing commitments are removed before invoking this calculation. */
public final class NetBillingDistribution {
    private NetBillingDistribution() { }

    public static Map<String, BigDecimal> allocate(BigDecimal netAmount, Map<String, BigDecimal> grossWeights) {
        BigDecimal amount = netAmount.setScale(2, RoundingMode.UNNECESSARY);
        if (grossWeights.values().stream().anyMatch(value -> value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2)) {
            throw new IllegalArgumentException("Net billing capacity weights must be non-negative canonical money");
        }
        BigDecimal total = grossWeights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        if (amount.signum() < 0 || amount.compareTo(total) > 0) throw new IllegalStateException("Contract net billing capacity requires financial reconciliation");
        var keys = grossWeights.keySet().stream().sorted().toList();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal remaining = amount;
        for (String key : keys) {
            BigDecimal share = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : grossWeights.get(key).multiply(amount).divide(total, 2, RoundingMode.DOWN);
            result.put(key, share);remaining = remaining.subtract(share);
        }
        for (int index = keys.size() - 1; index >= 0 && remaining.signum() > 0; index--) {
            String key = keys.get(index);
            BigDecimal extra = grossWeights.get(key).subtract(result.get(key)).min(remaining);
            result.put(key, result.get(key).add(extra));remaining = remaining.subtract(extra);
        }
        if (remaining.signum() != 0) throw new IllegalStateException("Net billing capacity rounding did not close");
        return Collections.unmodifiableMap(result);
    }

    public static BigDecimal consumeGross(BigDecimal requestedNet, BigDecimal availableNet, BigDecimal availableGross) {
        BigDecimal net = requestedNet.setScale(2, RoundingMode.UNNECESSARY);
        if (net.signum() <= 0 || net.compareTo(availableNet) > 0) throw new IllegalStateException("Requested net amount exceeds its origin capacity");
        return net.compareTo(availableNet) == 0 ? availableGross
                : net.multiply(availableGross).divide(availableNet, 2, RoundingMode.DOWN);
    }
}
