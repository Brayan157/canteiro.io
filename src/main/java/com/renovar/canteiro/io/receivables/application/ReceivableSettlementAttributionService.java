package com.renovar.canteiro.io.receivables.application;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class ReceivableSettlementAttributionService {
    private final ReceivableOriginRepository origins;
    private final BillingAllocationRepository allocations;
    private final ReceivableRepository receivables;
    private final TaxObligationRepository taxes;
    public void attribute(ReceivableSettlement settlement) {
        UUID company=settlement.getCompanyId();
        Receivable r=receivables.findByIdAndCompanyId(settlement.getReceivableId(),company).orElseThrow();
        origins.lockInstallment(company,r.getBillingInstallmentId());
        if(settlement.getEntryType()==ReceivableSettlementEntryType.REVERSAL){
            Map<UUID,BigDecimal> original=origins.settlementAttribution(company,settlement.getReversalOfSettlementId());
            original.forEach((id,amount)->origins.attributeSettlement(company,settlement.getId(),id,amount.negate()));
            return;
        }
        Receivable root=receivables.findByBillingInstallmentIdAndCompanyId(r.getBillingInstallmentId(),company).orElseThrow();
        Map<UUID,BigDecimal> weights=new LinkedHashMap<>();
        allocations.findByBillingInstallmentIdAndCompanyId(r.getBillingInstallmentId(),company).stream()
            .filter(a->a.getStatus()==BillingAllocationStatus.ACTIVE&&a.getType()==BillingAllocationType.SERVICE)
            .forEach(a->weights.put(a.getId(),a.getAllocatedAmount()));
        Map<UUID,BigDecimal> liabilities=ProportionalMoneyAllocation.allocate(root.getOriginalAmount(),weights);
        BigDecimal withheld=BigDecimal.ZERO,credited=BigDecimal.ZERO;
        for(Receivable title:receivables.findAllByBillingInstallmentIdAndCompanyId(r.getBillingInstallmentId(),company)) {
            withheld=withheld.add(taxes.registeredWithheldAmountByReceivableIdAndCompanyId(title.getId(),company));
            credited=credited.add(origins.effectiveCredits(company,title.getId()));
        }
        Map<UUID,BigDecimal> previous=origins.receivedByAllocation(company,r.getBillingInstallmentId());
        Map<UUID,BigDecimal> remaining=new LinkedHashMap<>();
        liabilities.forEach((id,value)->remaining.put(id,value.subtract(previous.getOrDefault(id,BigDecimal.ZERO))));
        if(remaining.values().stream().anyMatch(value->value.signum()<0))
            throw new com.renovar.canteiro.io.shared.api.error.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                com.renovar.canteiro.io.shared.api.error.ErrorCode.BUSINESS_RULE_VIOLATION,
                "Historical receipt attribution requires reconciliation before another settlement");
        Map<UUID,BigDecimal> adjustments=ProportionalMoneyAllocation.allocate(withheld.add(credited).setScale(2),remaining);
        remaining.replaceAll((id,value)->value.subtract(adjustments.get(id)));
        ProportionalMoneyAllocation.allocate(settlement.getAmount(),remaining)
            .forEach((id,amount)->origins.attributeSettlement(company,settlement.getId(),id,amount));
    }
}

