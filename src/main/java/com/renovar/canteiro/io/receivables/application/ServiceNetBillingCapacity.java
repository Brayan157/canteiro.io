package com.renovar.canteiro.io.receivables.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record ServiceNetBillingCapacity(NetBillingCapacityService.State state, Page<ServiceCapacity> services) {
    public record ServiceCapacity(UUID contractServiceId, String serviceName, int originCount, int usableOriginCount,
            BigDecimal availableNetAmount, BigDecimal baseNetAvailableAmount, BigDecimal measurementNetAvailableAmount, BigDecimal blockedNetAmount) { }
    public static ServiceCapacity group(UUID id, List<NetBillingCapacityService.Position> positions) {
        var own = positions.stream().filter(p -> p.contractServiceId().equals(id)).toList();
        BigDecimal base = BigDecimal.ZERO.setScale(2), measured = base, blocked = base;
        int usable = 0;
        for (var p : own) {
            if (!ResolveServiceNetBilling.usable(p)) blocked = blocked.add(p.netAvailableAmount());
            else { if (p.netAvailableAmount().signum() > 0) usable++; if (p.origin() == null) base = base.add(p.netAvailableAmount()); else measured = measured.add(p.netAvailableAmount()); }
        }
        return new ServiceCapacity(id, own.getFirst().serviceName(), own.size(), usable, base.add(measured), base, measured, blocked);
    }
}
