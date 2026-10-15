package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRecognition;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class MeasurementOriginRecognitionService {
    private final MeasurementContinuityRepository continuity;
    private final MeasurementItemRepository items;
    private final MeasurementServiceRepository groups;
    private final MeasurementDiscountRepository discounts;
    private final MeasurementContractCompositionRepository compositions;
    private final BillingAllocationRepository allocations;
    public void ensureBindings(UUID company,MeasurementVersion version) {
        Map<String,UUID> legacy=new LinkedHashMap<>();
        for(MeasurementItem item:items.findByMeasurementVersionIdAndCompanyId(version.getId(),company)) {
            if(continuity.serviceForItem(company,item.getId()).isPresent()) continue;
            if(item.getSourceItemId()!=null) continuity.inheritItemBinding(company,item.getSourceItemId(),item.getId(),version.getId());
            if(continuity.serviceForItem(company,item.getId()).isPresent()) continue;
            UUID sid=legacy.computeIfAbsent(item.getServiceTitle(),title->continuity.save(new MeasurementContinuousService(null,company,
                version.getMeasurementId(),title,continuity.findServices(company,version.getMeasurementId()).stream()
                    .mapToInt(MeasurementContinuousService::sortOrder).max().orElse(0)+1,null,null,0,null,null)).id());
            continuity.bindItem(company,version.getMeasurementId(),version.getId(),item.getId(),sid);
        }
    }
    public void validateCorrections(UUID company,MeasurementVersion version) {
        List<MeasurementItem> current=items.findByMeasurementVersionIdAndCompanyId(version.getId(),company);
        Map<UUID,BigDecimal> weights=new LinkedHashMap<>();Map<UUID,Set<UUID>> roots=new HashMap<>();
        for(MeasurementItem i:current) {
            UUID sid=continuity.serviceForItem(company,i.getId()).orElseThrow();
            weights.merge(sid,i.isActive()?i.getTotalAmount():zero(),BigDecimal::add);
            if(i.getSourceItemId()!=null) roots.computeIfAbsent(sid,k->new HashSet<>()).add(root(company,i.getSourceItemId()));
        }
        BigDecimal discount=MeasurementVersionAmountCalculator.calculate(current,discounts.findByMeasurementVersionIdAndCompanyId(version.getId(),company).orElse(null)).discountAmount();
        Map<UUID,BigDecimal> shares=com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation.allocate(discount,weights);
        List<MeasurementItemRecognition> effective=continuity.effectiveRecognitions(company,version.getMeasurementId());
        for(var entry:roots.entrySet()) {
            BigDecimal prior=effective.stream().filter(r->r.active()&&entry.getValue().contains(r.rootItemId()))
                .map(MeasurementItemRecognition::netAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(weights.get(entry.getKey()).subtract(shares.get(entry.getKey())).compareTo(prior)>=0) continue;
            for(MeasurementContractComposition c:compositions.findByMeasurementIdAndCompanyId(version.getMeasurementId(),company)) {
                if(continuity.isReversed(company,c.getId())) continue;
                boolean touched=c.getMeasurementItemIds().stream().map(id->root(company,id)).anyMatch(entry.getValue()::contains);
                if(touched&&allocations.findByMeasurementContractCompositionIdAndCompanyId(c.getId(),company).stream().anyMatch(a->a.getStatus()==BillingAllocationStatus.ACTIVE))
                    throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Cancel or reverse active billing of the reduced origin before accepting this corrective revision");
            }
        }
    }
    public void recognize(UUID company,MeasurementVersion version) {
        ensureBindings(company,version);
        if(!continuity.recognitionsForVersion(company,version.getId()).isEmpty()) return;
        List<MeasurementItem> all=items.findByMeasurementVersionIdAndCompanyId(version.getId(),company).stream()
            .sorted(Comparator.comparingInt(MeasurementItem::getItemNumber).thenComparing(i->i.getId().toString())).toList();
        Map<UUID,List<MeasurementItem>> byService=new LinkedHashMap<>();
        for(MeasurementItem item:all) byService.computeIfAbsent(continuity.serviceForItem(company,item.getId()).orElseThrow(),k->new ArrayList<>()).add(item);
        BigDecimal totalGross=all.stream().filter(MeasurementItem::isActive).map(MeasurementItem::getTotalAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal totalDiscount=MeasurementVersionAmountCalculator.calculate(all,discounts.findByMeasurementVersionIdAndCompanyId(version.getId(),company).orElse(null)).discountAmount();
        List<UUID> serviceIds=byService.keySet().stream().sorted(Comparator.comparingInt(id->continuity.findService(company,id).orElseThrow().sortOrder())).toList();
        Map<UUID,BigDecimal> groupWeights=new LinkedHashMap<>();
        for(UUID sid:serviceIds) groupWeights.put(sid,byService.get(sid).stream().filter(MeasurementItem::isActive)
            .map(MeasurementItem::getTotalAmount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2));
        Map<UUID,BigDecimal> groupDiscounts=com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation.allocate(totalDiscount,groupWeights);
        Set<UUID> uniqueRoots=new HashSet<>();
        for(UUID sid:serviceIds){
            List<MeasurementItem> serviceItems=byService.get(sid);
            Map<UUID,BigDecimal> itemWeights=new LinkedHashMap<>();
            for(MeasurementItem i:serviceItems) if(i.isActive()) itemWeights.put(i.getId(),i.getTotalAmount());
            Map<UUID,BigDecimal> shares=com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation.allocate(groupDiscounts.get(sid),itemWeights);
            for(MeasurementItem item:serviceItems) {
                BigDecimal share=shares.getOrDefault(item.getId(),zero());
                UUID root=root(company,item.getId());
                if(!uniqueRoots.add(root)) throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,"A corrective root occurs more than once");
                continuity.saveRecognition(new MeasurementItemRecognition(company,version.getMeasurementId(),version.getId(),item.getId(),
                    root,sid,item.isActive()?item.getTotalAmount():zero(),item.isActive()?item.getTotalAmount().subtract(share):zero(),
                    item.isActive()?item.getTotalWeightKg():null,item.isActive(),version.getVersionNumber()));
            }
            boolean exists=groups.findByMeasurementVersionIdAndCompanyId(version.getId(),company).stream()
                .anyMatch(group->continuity.serviceForGroup(company,group.getId()).filter(sid::equals).isPresent());
            if(!exists) {
                MeasurementContinuousService s=continuity.findService(company,sid).orElseThrow();
                MeasurementService group=groups.save(MeasurementService.create(company,version.getId(),s.serviceTitle(),
                    serviceItems.stream().mapToInt(MeasurementItem::getItemNumber).min().orElseThrow()));
                continuity.bindGroup(company,group.getId(),sid);
            }
        }
    }
    public UUID root(UUID company,UUID itemId) {
        Optional<MeasurementItemRecognition> recognized=continuity.recognitionForItem(company,itemId);
        if(recognized.isPresent()) return recognized.get().rootItemId();
        Set<UUID> visited=new HashSet<>(); UUID current=itemId;
        while(visited.add(current)) {
            MeasurementItem i=items.findByIdAndCompanyId(current,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement source item"));
            if(i.getSourceItemId()==null) return current;
            current=i.getSourceItemId();
        }
        throw new IllegalStateException("Measurement source cycle detected");
    }
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
}

