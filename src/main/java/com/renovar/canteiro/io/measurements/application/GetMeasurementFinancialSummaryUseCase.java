package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionFinancialPosition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionFinancialSource;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary;
import com.renovar.canteiro.io.measurements.domain.MeasurementRecognitionTotals;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
@Service @RequiredArgsConstructor
public class GetMeasurementFinancialSummaryUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final MeasurementRepository measurements;
    private final MeasurementContinuityRepository continuity;
    private final MeasurementContractCompositionRepository compositions;
    private final BillingAllocationRepository allocations;
    private final ReceivableRepository receivables;
    private final ReceivableSettlementRepository settlements;
    private final TaxObligationRepository taxes;
    private final ReceivableOriginRepository financialOrigins;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository legacyConversions;
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public MeasurementFinancialSummary get(UUID mid) {
        auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.READ);
        auth.requirePermission(AccessModule.BILLING,AccessAction.READ);
        auth.requirePermission(AccessModule.RECEIVABLES,AccessAction.READ);
        return project(mid);
    }
    /** Called only by tenant-scoped query/export use cases after their own measurement authorization. */
    @Transactional(readOnly=true)
    public MeasurementFinancialSummary project(UUID mid) {
        UUID company=tenants.requireCurrentTenant().companyId();
        Measurement m=measurements.findByIdAndCompanyId(mid,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement"));
        List<MeasurementRecognitionTotals> totals=continuity.effectiveTotals(company,mid);
        BigDecimal accepted=totals.stream().map(MeasurementRecognitionTotals::netAmount).reduce(zero(),BigDecimal::add);
        boolean allowed=auth.hasPermission(AccessModule.BILLING,AccessAction.READ)&&auth.hasPermission(AccessModule.RECEIVABLES,AccessAction.READ);
        String availability=m.getContractId()==null?"NO_CONTRACT":!allowed?"NOT_AUTHORIZED":
            legacyConversions.existsByMeasurementIdAndCompanyId(mid,company)?"LEGACY_ORIGIN_REQUIRES_RECONCILIATION":"AVAILABLE";
        if(!availability.equals("AVAILABLE")) return new MeasurementFinancialSummary(mid,m.getContractId(),accepted,null,null,null,null,
            List.of(),availability,Instant.now(),null,null,null,m.getCurrency());
        try {
        List<MeasurementCompositionFinancialSource> active=compositions.findActiveFinancialSources(mid,company);
        List<MeasurementCompositionFinancialPosition> positions=new ArrayList<>();
        BigDecimal uncomposed=zero();
        boolean reconciliationRequired=false;
        for(MeasurementRecognitionTotals t:totals) {
            MeasurementContinuousService s=continuity.findService(company,t.continuousServiceId()).orElseThrow();
            List<MeasurementCompositionFinancialSource> source=active.stream()
                .filter(c->continuity.serviceForGroup(company,c.getMeasurementServiceId()).filter(s.id()::equals).isPresent()).toList();
            BigDecimal recognized=source.stream().map(MeasurementCompositionFinancialSource::getNetAmount).reduce(zero(),BigDecimal::add);
            uncomposed=uncomposed.add(t.netAmount().subtract(recognized).max(zero()));
            BigDecimal billed=zero(),received=zero(),free=zero(),adjustment=zero(),outstanding=zero();
            for(MeasurementCompositionFinancialSource c:source) {
                List<BillingAllocation> lines=allocations.findByMeasurementContractCompositionIdAndCompanyId(c.getId(),company).stream()
                    .filter(a->a.getStatus()==BillingAllocationStatus.ACTIVE&&a.getType()==BillingAllocationType.SERVICE).toList();
                BigDecimal grossBilled=lines.stream().map(BillingAllocation::getAllocatedAmount).reduce(zero(),BigDecimal::add);
                free=free.add(c.getNetAmount().subtract(grossBilled).max(zero()));
                for(BillingAllocation a:lines) {
                    Attributed amounts=attribution(company,a);
                    billed=billed.add(amounts.billed());received=received.add(amounts.received());
                    adjustment=adjustment.add(amounts.adjustment());outstanding=outstanding.add(amounts.outstanding());
                }
            }
            MeasurementCompositionFinancialSource single=source.size()==1?source.getFirst():null;
            if(t.netAmount().compareTo(recognized)<0) {free=zero();reconciliationRequired=true;}
            positions.add(new MeasurementCompositionFinancialPosition(single==null?null:single.getId(),single==null?null:single.getMeasurementVersionId(),
                single==null?null:single.getMeasurementServiceId(),s.serviceTitle(),s.contractServiceId(),t.netAmount(),billed,received,free,outstanding,
                s.id(),source.stream().map(MeasurementCompositionFinancialSource::getId).toList(),
                t.netAmount().subtract(received).subtract(adjustment).max(zero()),adjustment));
        }
        return new MeasurementFinancialSummary(mid,m.getContractId(),accepted,sum(positions,MeasurementCompositionFinancialPosition::billedAmount),
            sum(positions,MeasurementCompositionFinancialPosition::receivedAmount),sum(positions,MeasurementCompositionFinancialPosition::acceptedAvailableToBill),
            sum(positions,MeasurementCompositionFinancialPosition::billedOutstandingAmount),positions,
            reconciliationRequired?"RECONCILIATION_REQUIRED":uncomposed.signum()>0?"PARTIALLY_COMPOSED":"AVAILABLE",Instant.now(),sum(positions,MeasurementCompositionFinancialPosition::remainingToReceive),
            sum(positions,MeasurementCompositionFinancialPosition::financialAdjustmentAmount),uncomposed,m.getCurrency());
        } catch(UnreconciledFinancialOriginException e) {
            // Preserve immutable history and documentary visibility; do not invent cash
            // or silently repair an inconsistent historical allocation during a GET.
            return new MeasurementFinancialSummary(mid,m.getContractId(),accepted,null,null,null,null,List.of(),
                "LEGACY_ORIGIN_REQUIRES_RECONCILIATION",Instant.now(),null,null,null,m.getCurrency());
        }
    }
    private Attributed attribution(UUID company,BillingAllocation allocation) {
        Optional<Receivable> title=receivables.findByBillingInstallmentIdAndCompanyId(allocation.getBillingInstallmentId(),company);
        if(title.isEmpty()) throw new IllegalStateException("Active billing lacks its official receivable");
        Receivable r=title.get();
        Map<UUID,BigDecimal> weights=new LinkedHashMap<>();
        allocations.findByBillingInstallmentIdAndCompanyId(r.getBillingInstallmentId(),company).stream()
            .filter(a->a.getStatus()==BillingAllocationStatus.ACTIVE&&a.getType()==BillingAllocationType.SERVICE)
            .forEach(a->weights.put(a.getId(),a.getAllocatedAmount()));
        if(r.getOriginalAmount().compareTo(weights.values().stream().reduce(zero(),BigDecimal::add))>0)
            throw new UnreconciledFinancialOriginException();
        Map<UUID,BigDecimal> billedShares=ProportionalMoneyAllocation.allocate(r.getOriginalAmount().setScale(2),weights);
        Map<UUID,BigDecimal> receipts=financialOrigins.receivedByAllocation(company,r.getBillingInstallmentId());
        if(receipts.entrySet().stream().anyMatch(e->e.getValue().signum()<0
            ||e.getValue().compareTo(billedShares.getOrDefault(e.getKey(),zero()))>0))throw new UnreconciledFinancialOriginException();
        Map<UUID,BigDecimal> remaining=new LinkedHashMap<>();
        billedShares.forEach((id,value)->remaining.put(id,value.subtract(receipts.getOrDefault(id,zero()))));
        BigDecimal deductions=zero();
        for(Receivable titlePart:receivables.findAllByBillingInstallmentIdAndCompanyId(r.getBillingInstallmentId(),company)) {
            deductions=deductions.add(taxes.registeredWithheldAmountByReceivableIdAndCompanyId(titlePart.getId(),company))
                .add(financialOrigins.effectiveCredits(company,titlePart.getId()));
        }
        if(deductions.compareTo(remaining.values().stream().reduce(zero(),BigDecimal::add))>0)
            throw new UnreconciledFinancialOriginException();
        Map<UUID,BigDecimal> adjustmentShares=ProportionalMoneyAllocation.allocate(deductions.setScale(2),remaining);
        BigDecimal billed=billedShares.get(allocation.getId());
        BigDecimal received=receipts.getOrDefault(allocation.getId(),zero());
        BigDecimal contractAdjustment=allocation.getAllocatedAmount().subtract(billed);
        BigDecimal adjustment=adjustmentShares.get(allocation.getId());
        return new Attributed(billed,received,contractAdjustment.add(adjustment),billed.subtract(adjustment).subtract(received));
    }
    private BigDecimal share(BigDecimal amount,Map<UUID,BigDecimal> weights,UUID id) {
        return ProportionalMoneyAllocation.allocate(amount.setScale(2),weights).getOrDefault(id,zero());
    }
    private BigDecimal sum(List<MeasurementCompositionFinancialPosition> entries,java.util.function.Function<MeasurementCompositionFinancialPosition,BigDecimal> f){
        return entries.stream().map(f).reduce(zero(),BigDecimal::add).setScale(2);
    }
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private record Attributed(BigDecimal billed,BigDecimal received,BigDecimal adjustment,BigDecimal outstanding){}
    private static final class UnreconciledFinancialOriginException extends RuntimeException { }
}
