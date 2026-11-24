package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Current prospective capacities; never rewrites historical cash or distributes cash receipts. */
public record InvoiceWithholdingCapacity(boolean withheld, String unavailableReason, Map<UUID, Item> items) {
    public record Item(BigDecimal withheldAmount, BigDecimal netAmount, BigDecimal availableAmount, String unavailableReason) {
        public static Item unavailable(String reason) { return new Item(null,null,null,reason); }
    }

    public Item item(InvoiceSettlementRepository.Candidate candidate) {
        if (unavailableReason != null) return Item.unavailable(unavailableReason);
        if (!withheld) return new Item(BigDecimal.ZERO.setScale(2), candidate.invoicedItemAmount(),
                candidate.invoicedItemAmount().subtract(candidate.itemReceivedAmount()), null);
        return items.getOrDefault(candidate.invoiceItemId(), Item.unavailable("SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION"));
    }

    public static Map<UUID, InvoiceWithholdingCapacity> calculate(List<InvoiceSettlementRepository.WithholdingBasis> rows) {
        Map<UUID, InvoiceWithholdingCapacity> result = new HashMap<>();
        rows.stream().collect(Collectors.groupingBy(InvoiceSettlementRepository.WithholdingBasis::invoiceId))
                .forEach((invoice, basis) -> result.put(invoice, calculateInvoice(basis)));
        return Map.copyOf(result);
    }

    private static InvoiceWithholdingCapacity calculateInvoice(List<InvoiceSettlementRepository.WithholdingBasis> rows) {
        var first = rows.getFirst();
        if (first.withheldAmount().signum() == 0) return new InvoiceWithholdingCapacity(false,null,Map.of());
        if (first.itemCount() > 100) return unavailable("SETTLEMENT_WITHHOLDING_TOO_MANY_ITEMS");
        if (!first.taxScopeProven() || rows.stream().anyMatch(row -> !row.itemOriginProven()))
            return unavailable("SETTLEMENT_WITHHOLDING_SCOPE_REQUIRES_RECONCILIATION");
        Map<UUID,BigDecimal> weights = new LinkedHashMap<>();
        for (var row : rows) weights.put(row.invoiceItemId(),row.itemAmount().setScale(2));
        BigDecimal gross = weights.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        if (rows.size()!=first.itemCount() || gross.compareTo(first.invoiceAmount())!=0
                || first.withheldAmount().signum()<0 || first.withheldAmount().compareTo(gross)>0)
            return unavailable("SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION");
        var shares = ProportionalMoneyAllocation.allocate(first.withheldAmount(),weights);
        Map<UUID,Item> items = new HashMap<>();
        for (var row : rows) {
            BigDecimal tax = shares.get(row.invoiceItemId());
            BigDecimal net = row.itemAmount().subtract(tax).setScale(2);
            if (row.itemReceivedAmount().compareTo(net)>0)
                return unavailable("SETTLEMENT_WITHHOLDING_HISTORY_REQUIRES_RECONCILIATION");
            items.put(row.invoiceItemId(),new Item(tax,net,net.subtract(row.itemReceivedAmount()).setScale(2),null));
        }
        return new InvoiceWithholdingCapacity(true,null,Map.copyOf(items));
    }

    private static InvoiceWithholdingCapacity unavailable(String reason) {
        return new InvoiceWithholdingCapacity(true,reason,Map.of());
    }
}
