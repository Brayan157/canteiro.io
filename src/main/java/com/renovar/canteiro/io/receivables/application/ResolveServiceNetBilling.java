package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** User-authorized policy: base first, official acceptance date next, immutable IDs break same-date ties. */
public final class ResolveServiceNetBilling {
    private ResolveServiceNetBilling() { }
    public static boolean usable(NetBillingCapacityService.Position p) {
        return p.eligible() && (p.origin() == null || p.origin().acceptedOn() != null);
    }
    public static List<CreateNetBillingCommand.Line> resolve(NetBillingCapacityService.State state, List<CreateNetBillingCommand.ServiceLine> intent) {
        if (intent == null || intent.isEmpty() || intent.size() > 100) throw new IllegalArgumentException("Provide 1 to 100 services");
        var ids = new HashSet<UUID>();
        List<CreateNetBillingCommand.Line> result = new ArrayList<>();
        for (var line : intent) {
            if (line == null || line.contractServiceId() == null || line.mode() == null || !ids.add(line.contractServiceId())) throw new IllegalArgumentException("Each service must appear exactly once with an amount mode");
            if (line.mode() == CreateNetBillingCommand.ServiceAmountMode.ALL_AVAILABLE && line.netAmount() != null
                    || line.mode() == CreateNetBillingCommand.ServiceAmountMode.PARTIAL && (line.netAmount() == null || line.netAmount().signum() <= 0 || line.netAmount().stripTrailingZeros().scale() > 2)) {
                throw new IllegalArgumentException("PARTIAL requires positive canonical money; ALL_AVAILABLE forbids an amount");
            }
            var all = state.positions().stream().filter(p -> p.contractServiceId().equals(line.contractServiceId())).toList();
            if (all.isEmpty()) throw new TenantResourceNotFoundException("Contract service");
            var positions = all.stream().filter(ResolveServiceNetBilling::usable).sorted(Comparator
                    .comparingInt((NetBillingCapacityService.Position p) -> p.origin() == null ? 0 : 1)
                    .thenComparing(p -> p.origin() == null ? java.time.LocalDate.MIN : p.origin().acceptedOn())
                    .thenComparing(p -> p.origin() == null ? "" : p.origin().measurementId().toString())
                    .thenComparing(p -> p.origin() == null ? "" : p.origin().measurementVersionId().toString())
                    .thenComparing(p -> p.origin() == null ? "" : p.origin().id().toString())).toList();
            BigDecimal available = positions.stream().map(NetBillingCapacityService.Position::netAvailableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = line.mode() == CreateNetBillingCommand.ServiceAmountMode.ALL_AVAILABLE ? available : line.netAmount();
            if (remaining.signum() <= 0 || remaining.compareTo(available) > 0) throw new IllegalStateException("Service has insufficient usable net capacity");
            for (var p : positions) {
                BigDecimal take = remaining.min(p.netAvailableAmount()).setScale(2);
                if (take.signum() > 0) result.add(new CreateNetBillingCommand.Line(p.contractServiceId(), p.origin() == null ? null : p.origin().id(), take));
                remaining = remaining.subtract(take);
            }
        }
        if (result.size() > 10000) throw new IllegalStateException("Resolved billing exceeds 10000 exact origins");
        return List.copyOf(result);
    }
}
