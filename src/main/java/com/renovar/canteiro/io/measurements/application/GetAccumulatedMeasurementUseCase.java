package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionState;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRecognition;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedItemResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedServiceResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementContinuousServiceResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementFinancialSummaryResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementVersionResponse;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class GetAccumulatedMeasurementUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final MeasurementRepository measurements;
    private final MeasurementVersionRepository versions;
    private final MeasurementItemRepository items;
    private final MeasurementContinuityRepository continuity;
    private final MeasurementDiscountRepository discounts;
    private final GetMeasurementFinancialSummaryUseCase financial;
    private final MeasurementCompositionProgressService progress;
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public MeasurementAccumulatedResponse get(UUID mid) {
        auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.READ);
        UUID company=tenants.requireCurrentTenant().companyId();
        Measurement m=measurements.findByIdAndCompanyId(mid,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement"));
        MeasurementVersion current=versions.findLatestByMeasurementIdAndCompanyId(mid,company).orElseThrow();
        List<MeasurementItemRecognition> recognized=continuity.effectiveRecognitions(company,mid).stream().filter(MeasurementItemRecognition::active).toList();
        List<MeasurementItem> pending=current.getStatus()==MeasurementVersionStatus.ACCEPTED?List.of():
            items.findByMeasurementVersionIdAndCompanyId(current.getId(),company);
        MeasurementVersionAmounts pendingAmounts=MeasurementVersionAmountCalculator.calculate(pending,
            current.getStatus()==MeasurementVersionStatus.ACCEPTED?null:discounts.findByMeasurementVersionIdAndCompanyId(current.getId(),company).orElse(null));
        Map<UUID,BigDecimal> pendingWeights=new LinkedHashMap<>();
        for(MeasurementItem item:pending) pendingWeights.merge(pendingServiceKey(company,item,pending),
            item.isActive()?item.getTotalAmount():zero(),BigDecimal::add);
        Map<UUID,BigDecimal> pendingDiscounts=com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation
            .allocate(pendingAmounts.discountAmount(),pendingWeights);
        Map<UUID,MeasurementItemRecognition> acceptedByRoot=new HashMap<>();
        recognized.forEach(r->acceptedByRoot.put(r.rootItemId(),r));
        List<MeasurementAccumulatedServiceResponse> result=new ArrayList<>();
        List<MeasurementCompositionState> states=progress.inspect(mid);
        for(MeasurementContinuousService s:continuity.findServices(company,mid)) {
            List<MeasurementItemRecognition> serviceAccepted=recognized.stream().filter(r->s.id().equals(r.continuousServiceId())).toList();
            List<MeasurementAccumulatedItemResponse> lines=new ArrayList<>();
            for(MeasurementItemRecognition r:serviceAccepted) {
                MeasurementItem i=items.findByIdAndCompanyId(r.measurementItemId(),company).orElseThrow();
                lines.add(line(i,s.id(),r.rootItemId(),r.measurementVersionId(),r.versionNumber(),"ACCEPTED",false));
            }
            List<MeasurementItem> servicePending=pending.stream().filter(i->continuity.serviceForItem(company,i.getId()).filter(s.id()::equals).isPresent()).toList();
            for(MeasurementItem i:servicePending) {
                if(isPendingProposal(company,i)) lines.add(line(i,s.id(),root(company,i),current.getId(),current.getVersionNumber(),
                    current.getStatus().name(),current.getStatus()==MeasurementVersionStatus.DRAFT&&i.isActive()));
            }
            // Legacy unbound drafts are projected under a temporary, explicitly unbound group below.
            lines.sort(Comparator.comparingInt(MeasurementAccumulatedItemResponse::versionNumber)
                .thenComparingInt(l->l.item().itemNumber()).thenComparing(l->l.item().id().toString()));
            BigDecimal acceptedGross=sum(serviceAccepted,MeasurementItemRecognition::grossAmount),acceptedNet=sum(serviceAccepted,MeasurementItemRecognition::netAmount);
            BigDecimal acceptedWeight=weight(serviceAccepted);
            BigDecimal pg=servicePending.stream().filter(MeasurementItem::isActive).map(MeasurementItem::getTotalAmount).reduce(zero(),BigDecimal::add);
            BigDecimal pn=pg.subtract(pendingDiscounts.getOrDefault(s.id(),zero()));
            BigDecimal pw=servicePending.stream().filter(MeasurementItem::isActive).map(MeasurementItem::getTotalWeightKg).filter(Objects::nonNull).reduce(BigDecimal.ZERO.setScale(4),BigDecimal::add);
            for(MeasurementItem i:servicePending) {
                MeasurementItemRecognition previous=sourceRecognition(company,i);
                if(previous!=null) {pg=pg.subtract(previous.grossAmount());pn=pn.subtract(previous.netAmount());if(previous.totalWeightKg()!=null)pw=pw.subtract(previous.totalWeightKg());}
            }
            result.add(new MeasurementAccumulatedServiceResponse(MeasurementContinuousServiceResponse.from(s),List.copyOf(lines),
                MeasurementAccumulatedTotalsResponse.of(serviceAccepted.size(),acceptedGross,acceptedNet,acceptedWeight),
                MeasurementAccumulatedTotalsResponse.of(lines.stream().filter(l->!l.status().equals("ACCEPTED")).count(),pg,pn,pw),
                states.stream().filter(st->s.id().equals(st.continuousServiceId())).toList()));
        }
        List<MeasurementItem> unbound=pending.stream().filter(i->continuity.serviceForItem(company,i.getId()).isEmpty()).toList();
        for(String title:unbound.stream().map(MeasurementItem::getServiceTitle).distinct().toList()){
            List<MeasurementItem> group=unbound.stream().filter(i->title.equals(i.getServiceTitle())).toList();
            List<MeasurementAccumulatedItemResponse> lines=group.stream().filter(MeasurementItem::isActive)
                .map(i->line(i,null,root(company,i),current.getId(),current.getVersionNumber(),current.getStatus().name(),current.getStatus()==MeasurementVersionStatus.DRAFT)).toList();
            BigDecimal gross=group.stream().filter(MeasurementItem::isActive).map(MeasurementItem::getTotalAmount).reduce(zero(),BigDecimal::add);
            BigDecimal net=gross.subtract(pendingDiscounts.getOrDefault(pendingServiceKey(company,group.getFirst(),pending),zero()));
            BigDecimal groupWeight=group.stream().filter(MeasurementItem::isActive).map(MeasurementItem::getTotalWeightKg)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO.setScale(4),BigDecimal::add);
            result.add(new MeasurementAccumulatedServiceResponse(new MeasurementContinuousServiceResponse(null,mid,title,0,null,null,null,null,null,null,null),
                lines,MeasurementAccumulatedTotalsResponse.of(0,zero(),zero(),null),MeasurementAccumulatedTotalsResponse.of(lines.size(),gross,net,groupWeight),List.of()));
        }
        BigDecimal ag=sum(recognized,MeasurementItemRecognition::grossAmount),an=sum(recognized,MeasurementItemRecognition::netAmount),aw=weight(recognized);
        BigDecimal pg=pendingAmounts.grossAmount(),pn=pendingAmounts.netAmount(),pw=pendingAmounts.totalWeightKg();
        for(MeasurementItem i:pending){
            MeasurementItemRecognition previous=sourceRecognition(company,i);
            if(previous!=null){pg=pg.subtract(previous.grossAmount());pn=pn.subtract(previous.netAmount());if(previous.totalWeightKg()!=null)pw=pw.subtract(previous.totalWeightKg());}
        }
        long pendingCount=result.stream().flatMap(s->s.items().stream()).filter(l->!l.status().equals("ACCEPTED")).count();
        MeasurementFinancialSummary finance=financial.project(mid);
        return new MeasurementAccumulatedResponse(MeasurementResponse.from(m),MeasurementVersionResponse.from(current),List.copyOf(result),
            MeasurementAccumulatedTotalsResponse.of(recognized.size(),ag,an,aw),MeasurementAccumulatedTotalsResponse.of(pendingCount,pg,pn,pw),
            MeasurementAccumulatedTotalsResponse.of(recognized.size()+pendingCount,ag.add(pg),an.add(pn),aw.add(pw)),
            MeasurementFinancialSummaryResponse.from(finance),finance.asOf());
    }
    public boolean isPendingProposal(UUID company,MeasurementItem item) {
        MeasurementItemRecognition previous=sourceRecognition(company,item);
        if(previous==null)return item.isActive();
        MeasurementItem source=items.findByIdAndCompanyId(previous.measurementItemId(),company).orElseThrow();
        return item.isActive()!=source.isActive()||item.getTotalAmount().compareTo(source.getTotalAmount())!=0
            ||!Objects.equals(item.getActivity(),source.getActivity())||!Objects.equals(item.getDescription(),source.getDescription())
            ||!Objects.equals(item.getMeasuredOn(),source.getMeasuredOn())||item.getChargeType()!=source.getChargeType()
            ||!Objects.equals(item.getUnitPrice(),source.getUnitPrice())||!Objects.equals(item.getTotalWeightKg(),source.getTotalWeightKg())
            ||!Objects.equals(item.getUnitQuantity(),source.getUnitQuantity())||!Objects.equals(item.getAreaSquareMeters(),source.getAreaSquareMeters())
            ||!Objects.equals(item.getLinearMeters(),source.getLinearMeters());
    }
    public UUID root(UUID company,MeasurementItem i){MeasurementItemRecognition r=sourceRecognition(company,i);return r==null?i.getId():r.rootItemId();}
    public MeasurementItemRecognition sourceRecognition(UUID company,MeasurementItem i){
        Set<UUID> visited=new HashSet<>(); UUID source=i.getSourceItemId();
        while(source!=null&&visited.add(source)) {
            var recognition=continuity.recognitionForItem(company,source);
            if(recognition.isPresent())return recognition.get();
            source=items.findByIdAndCompanyId(source,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement source item")).getSourceItemId();
        }
        if(source!=null)throw new IllegalStateException("Measurement source cycle");
        return null;
    }
    private UUID pendingServiceKey(UUID company,MeasurementItem item,List<MeasurementItem> pending) {
        return continuity.serviceForItem(company,item.getId()).orElseGet(()->pending.stream()
            .filter(i->Objects.equals(i.getServiceTitle(),item.getServiceTitle())&&continuity.serviceForItem(company,i.getId()).isEmpty())
            .map(MeasurementItem::getId).min(Comparator.comparing(UUID::toString)).orElseThrow());
    }
    public MeasurementAccumulatedItemResponse line(MeasurementItem i,UUID sid,UUID root,UUID vid,int number,String status,boolean editable){
        String presentation=status.equals("ACCEPTED")?"Aceito":i.isActive()?"Em revisão / aguardando aceite":"Anulação em revisão";
        return new MeasurementAccumulatedItemResponse(MeasurementItemResponse.from(i),sid,root,vid,number,number+"."+i.getItemNumber(),status,presentation,editable);
    }
    private BigDecimal sum(List<MeasurementItemRecognition> rows,java.util.function.Function<MeasurementItemRecognition,BigDecimal> f){return rows.stream().map(f).reduce(zero(),BigDecimal::add).setScale(2);}
    private BigDecimal weight(List<MeasurementItemRecognition> rows){return rows.stream().map(MeasurementItemRecognition::totalWeightKg).filter(Objects::nonNull).reduce(BigDecimal.ZERO.setScale(4),BigDecimal::add);}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
}

