package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** One commercial service in one charge, including every exact active base/composition origin. No ledger rewrite. */
public final class InvoiceServiceCoverage {
    private InvoiceServiceCoverage() { }
    public enum State { NOT_DOCUMENTED, FULLY_DOCUMENTED, LEGACY_PARTIAL, REVIEW_REQUIRED }
    public record Origin(UUID billingAllocationId,UUID measurementContractCompositionId,BigDecimal amount,BigDecimal documentedAmount) { }
    public record Group(UUID id,UUID billingInstallmentId,UUID contractServiceId,BigDecimal amount,
            BigDecimal documentedAmount,State state,List<Origin> origins){
        public BigDecimal availableToDocumentAmount(){return state==State.REVIEW_REQUIRED||documentedAmount==null?null:amount.subtract(documentedAmount);}
        public boolean selectable(){return state==State.NOT_DOCUMENTED;}
        public String blockingReason(){return switch(state){
            case NOT_DOCUMENTED -> null;
            case FULLY_DOCUMENTED -> "INVOICE_SERVICE_GROUP_ALREADY_DOCUMENTED";
            case LEGACY_PARTIAL -> "INVOICE_SERVICE_GROUP_LEGACY_PARTIAL";
            case REVIEW_REQUIRED -> "INVOICE_SERVICE_GROUP_REVIEW_REQUIRED";
        };}
    }
    private record Key(UUID installment,UUID service) { }
    public static List<Group> groups(UUID company,UUID contract,Collection<BillingAllocation> allocations,Map<UUID,BigDecimal> documented){
        Map<Key,List<BillingAllocation>> grouped=new LinkedHashMap<>();Set<UUID> review=new java.util.HashSet<>();
        for(var a:allocations){
            if(!company.equals(a.getCompanyId())||!contract.equals(a.getContractId()))throw new InvoiceServiceCoverageViolation("INVOICE_SERVICE_GROUP_REVIEW_REQUIRED");
            if(a.getStatus()!=BillingAllocationStatus.ACTIVE)continue;
            if(a.getType()!=BillingAllocationType.SERVICE||a.getContractServiceId()==null||a.getAllocatedAmount().signum()<=0){review.add(a.getBillingInstallmentId());continue;}
            grouped.computeIfAbsent(new Key(a.getBillingInstallmentId(),a.getContractServiceId()),k->new ArrayList<>()).add(a);
        }
        List<Group> result=new ArrayList<>();
        for(var e:grouped.entrySet()){
            var key=e.getKey();var origins=e.getValue().stream().sorted(Comparator.comparing(a->a.getId().toString()))
                    .map(a->new Origin(a.getId(),a.getMeasurementContractCompositionId(),a.getAllocatedAmount(),documented.get(a.getId()))).toList();
            boolean known=origins.stream().allMatch(o->o.documentedAmount()!=null&&o.documentedAmount().signum()>=0&&o.documentedAmount().compareTo(o.amount())<=0);
            State state=!known||review.contains(key.installment())?State.REVIEW_REQUIRED:
                    origins.stream().allMatch(o->o.documentedAmount().signum()==0)?State.NOT_DOCUMENTED:
                    origins.stream().allMatch(o->o.documentedAmount().compareTo(o.amount())==0)?State.FULLY_DOCUMENTED:State.LEGACY_PARTIAL;
            UUID id=UUID.nameUUIDFromBytes(("invoice-service-group:v1/"+company+"/"+contract+"/"+key.installment()+"/"+key.service()).getBytes(StandardCharsets.UTF_8));
            result.add(new Group(id,key.installment(),key.service(),sum(origins.stream().map(Origin::amount).toList()),
                    known?sum(origins.stream().map(Origin::documentedAmount).toList()):null,state,origins));
        }
        return result.stream().sorted(Comparator.comparing(g->g.id().toString())).toList();
    }
    public static void requireCompleteSelection(List<Group> groups,Map<UUID,BigDecimal> requested){
        Set<UUID> found=new java.util.HashSet<>();
        for(var group:groups){
            Set<UUID> ids=group.origins().stream().map(Origin::billingAllocationId).collect(Collectors.toSet());
            if(ids.stream().noneMatch(requested::containsKey))continue;
            if(!group.selectable())throw new InvoiceServiceCoverageViolation(group.blockingReason());
            for(var origin:group.origins()){
                BigDecimal value=requested.get(origin.billingAllocationId());
                if(value==null||value.compareTo(origin.amount())!=0)throw new InvoiceServiceCoverageViolation("INVOICE_SERVICE_GROUP_INCOMPLETE");
                found.add(origin.billingAllocationId());
            }
        }
        if(!found.equals(requested.keySet()))throw new InvoiceServiceCoverageViolation("INVOICE_SERVICE_GROUP_REVIEW_REQUIRED");
    }
    private static BigDecimal sum(List<BigDecimal> values){return values.stream().reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);}
}
