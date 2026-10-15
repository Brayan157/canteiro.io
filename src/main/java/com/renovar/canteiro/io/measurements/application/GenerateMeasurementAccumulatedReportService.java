package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRecognition;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRecognitionTotals;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.MDC;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
import com.renovar.canteiro.io.shared.domain.ProportionalMoneyAllocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.OutputStream;
@Service @RequiredArgsConstructor
public class GenerateMeasurementAccumulatedReportService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final MeasurementRepository measurements;
    private final MeasurementVersionRepository versions;
    private final MeasurementItemRepository items;
    private final MeasurementDiscountRepository discounts;
    private final MeasurementContinuityRepository continuity;
    private final GetAccumulatedMeasurementUseCase accumulated;
    private final GetMeasurementFinancialSummaryUseCase financial;
    private final MeasurementCompanyBrandingLoader branding;
    private final MeasurementAccumulatedReportGenerator generator;
    private final PlatformTransactionManager transactionManager;
    private final AuditEventRecorder audit;
    public GeneratedMeasurementPdf generate(UUID mid,boolean spreadsheet) {
        auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.EXPORT);
        TenantContext tenant=tenants.requireCurrentTenant();
        Measurement m=measurements.findByIdAndCompanyId(mid,tenant.companyId()).orElseThrow(()->new TenantResourceNotFoundException("Measurement"));
        String filename=spreadsheet?MeasurementExportFilename.spreadsheet(m.getTitle()):MeasurementExportFilename.pdf(m.getTitle());
        String correlation=MDC.get("X-Correlation-Id");
        return new GeneratedMeasurementPdf(filename,output->write(tenant,correlation,mid,spreadsheet,output));
    }
    private void write(TenantContext tenant,String correlation,UUID mid,boolean spreadsheet,OutputStream output) throws IOException {
        tenants.setCurrentTenant(tenant);if(correlation!=null)MDC.put("X-Correlation-Id",correlation);
        try {
            TransactionTemplate snapshot=new TransactionTemplate(transactionManager);
            snapshot.setReadOnly(true);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            Long count;
            try {
                count=snapshot.execute(status->{
                    Measurement m=measurements.findByIdAndCompanyId(mid,tenant.companyId()).orElseThrow();
                    MeasurementVersion v=versions.findLatestByMeasurementIdAndCompanyId(mid,tenant.companyId()).orElseThrow();
                    MeasurementAccumulatedReportData data=header(tenant.companyId(),m,v);
                    MeasurementAccumulatedLineSource source=source(tenant.companyId(),m,v);
                    try { return spreadsheet?generator.writeSpreadsheet(data,source,output):generator.writePdf(data,source,output); }
                    catch(IOException e){throw new UncheckedIOException(e);}
                });
            } catch(UncheckedIOException e){throw e.getCause();}
            long written=count==null?0:count;
            new TransactionTemplate(transactionManager).executeWithoutResult(status->audit.recordDirectAction(AuditModule.MEASUREMENTS,
                AuditAction.EXPORT,"MeasurementAccumulatedReport",mid,null,Map.of("itemCount",written),
                Map.of("origin","direct","format",spreadsheet?"ACCUMULATED_XLSX":"ACCUMULATED_PDF","streamed",true)));
        } finally {tenants.clear();MDC.remove("X-Correlation-Id");}
    }
    private MeasurementAccumulatedReportData header(UUID company,Measurement m,MeasurementVersion v) {
        List<MeasurementRecognitionTotals> totals=continuity.effectiveTotals(company,m.getId());
        List<MeasurementContinuousService> services=continuity.findServices(company,m.getId());
        Map<UUID,DraftSubtotal> byService=new LinkedHashMap<>();
        Map<String,DraftSubtotal> unbound=new LinkedHashMap<>();
        services.forEach(s->byService.put(s.id(),new DraftSubtotal(s.id(),s.serviceTitle())));
        BigDecimal ag=totals.stream().map(MeasurementRecognitionTotals::grossAmount).reduce(zero(),BigDecimal::add),
            an=totals.stream().map(MeasurementRecognitionTotals::netAmount).reduce(zero(),BigDecimal::add),
            aw=totals.stream().map(MeasurementRecognitionTotals::totalWeightKg).filter(Objects::nonNull).reduce(BigDecimal.ZERO.setScale(4),BigDecimal::add);
        BigDecimal pg=zero(),pw=BigDecimal.ZERO.setScale(4),priorGross=zero(),priorNet=zero(),priorWeight=BigDecimal.ZERO.setScale(4);
        long count=0;
        if(v.getStatus()!=MeasurementVersionStatus.ACCEPTED){
            int page=0;Page<MeasurementItem> batch;
            do {
                batch=items.findByMeasurementVersionIdAndCompanyId(v.getId(),company,PageRequest.of(page++,250,Sort.by("itemNumber","id")));
                for(MeasurementItem item:batch) {
                    UUID sid=continuity.serviceForItem(company,item.getId()).orElse(null);
                    DraftSubtotal service=sid==null?unbound.computeIfAbsent(item.getServiceTitle(),title->new DraftSubtotal(item.getId(),title)):
                        byService.get(sid);
                    if(service==null)throw new IllegalStateException("An official item cannot belong to a retired service");
                    if(sid==null&&item.getId().toString().compareTo(service.key.toString())<0)service.key=item.getId();
                    service.include(item,accumulated.sourceRecognition(company,item),accumulated.isPendingProposal(company,item));
                    if(item.isActive()){pg=pg.add(item.getTotalAmount());if(item.getTotalWeightKg()!=null)pw=pw.add(item.getTotalWeightKg());}
                    MeasurementItemRecognition old=accumulated.sourceRecognition(company,item);
                    if(old!=null){priorGross=priorGross.add(old.grossAmount());priorNet=priorNet.add(old.netAmount());if(old.totalWeightKg()!=null)priorWeight=priorWeight.add(old.totalWeightKg());}
                    if(accumulated.isPendingProposal(company,item))count++;
                }
                continuity.clearReadContext();
            }while(batch.hasNext());
        }
        var amounts=MeasurementVersionAmountCalculator.calculate(pw,pg,v.getStatus()==MeasurementVersionStatus.ACCEPTED?null:
            discounts.findByMeasurementVersionIdAndCompanyId(v.getId(),company).orElse(null));
        Map<UUID,BigDecimal> weights=new LinkedHashMap<>();
        byService.values().forEach(s->weights.put(s.key,s.gross));
        unbound.values().forEach(s->weights.put(s.key,s.gross));
        Map<UUID,BigDecimal> shares=ProportionalMoneyAllocation.allocate(amounts.discountAmount(),weights);
        List<MeasurementAccumulatedServiceSubtotal> subtotals=new ArrayList<>();
        for(MeasurementContinuousService service:services) {
            MeasurementRecognitionTotals accepted=totals.stream().filter(t->service.id().equals(t.continuousServiceId())).findFirst().orElse(null);
            subtotals.add(new MeasurementAccumulatedServiceSubtotal(service.id(),service.serviceTitle(),
                accepted==null?MeasurementAccumulatedTotalsResponse.of(0,zero(),zero(),null):
                    MeasurementAccumulatedTotalsResponse.of(accepted.itemCount(),accepted.grossAmount(),accepted.netAmount(),accepted.totalWeightKg()),
                byService.get(service.id()).totals(shares.getOrDefault(service.id(),zero()))));
        }
        unbound.values().forEach(s->subtotals.add(new MeasurementAccumulatedServiceSubtotal(null,s.title,
            MeasurementAccumulatedTotalsResponse.of(0,zero(),zero(),null),s.totals(shares.getOrDefault(s.key,zero())))));
        return new MeasurementAccumulatedReportData(m,v,branding.load(company),
            MeasurementAccumulatedTotalsResponse.of(totals.stream().mapToLong(MeasurementRecognitionTotals::itemCount).sum(),ag,an,aw),
            MeasurementAccumulatedTotalsResponse.of(count,pg.subtract(priorGross),amounts.netAmount().subtract(priorNet),pw.subtract(priorWeight)),
            financial.project(m.getId()),List.copyOf(subtotals));
    }
    private MeasurementAccumulatedLineSource source(UUID company,Measurement m,MeasurementVersion v) {
        List<MeasurementContinuousService> services=continuity.findServices(company,m.getId());
        return consumer->{
            for(MeasurementContinuousService service:services){
                consumer.accept(MeasurementAccumulatedLine.serviceHeader(service));
                int offset=0;List<MeasurementItemRecognition> batch;
                do {
                    batch=continuity.effectivePage(company,m.getId(),service.id(),offset,250);offset+=batch.size();
                    for(MeasurementItemRecognition r:batch){
                        MeasurementItem item=items.findByIdAndCompanyId(r.measurementItemId(),company).orElseThrow();
                        consumer.accept(new MeasurementAccumulatedLine(service.id(),service.serviceTitle(),item,r.versionNumber(),"ACCEPTED","Aceito"));
                    }
                    continuity.clearReadContext();
                }while(batch.size()==250);
                if(v.getStatus()!=MeasurementVersionStatus.ACCEPTED)pending(company,v,service.id(),service.serviceTitle(),consumer);
            }
            if(v.getStatus()!=MeasurementVersionStatus.ACCEPTED)pending(company,v,null,null,consumer);
        };
    }
    private void pending(UUID company,MeasurementVersion v,UUID sid,String title,MeasurementAccumulatedLineSource.LineConsumer consumer) throws IOException {
        int offset=0;List<UUID> batch;
        do {
            batch=continuity.pendingItemIdsPage(company,v.getId(),sid,offset,250);offset+=batch.size();
            for(UUID id:batch){
                MeasurementItem i=items.findByIdAndCompanyId(id,company).orElseThrow();
                if(accumulated.isPendingProposal(company,i))consumer.accept(new MeasurementAccumulatedLine(sid,title==null?i.getServiceTitle():title,
                    i,v.getVersionNumber(),v.getStatus().name(),i.isActive()?"Em revisão / aguardando aceite":"Anulação em revisão"));
            }
            continuity.clearReadContext();
        }while(batch.size()==250);
    }
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private static final class DraftSubtotal {
        private UUID key;
        private final String title;
        private BigDecimal gross=BigDecimal.ZERO.setScale(2),priorGross=BigDecimal.ZERO.setScale(2),priorNet=BigDecimal.ZERO.setScale(2);
        private BigDecimal weight=BigDecimal.ZERO.setScale(4),priorWeight=BigDecimal.ZERO.setScale(4);
        private long count;
        private DraftSubtotal(UUID key,String title){this.key=key;this.title=title;}
        private void include(MeasurementItem item,MeasurementItemRecognition previous,boolean proposal) {
            if(item.isActive()){gross=gross.add(item.getTotalAmount());if(item.getTotalWeightKg()!=null)weight=weight.add(item.getTotalWeightKg());}
            if(previous!=null){priorGross=priorGross.add(previous.grossAmount());priorNet=priorNet.add(previous.netAmount());
                if(previous.totalWeightKg()!=null)priorWeight=priorWeight.add(previous.totalWeightKg());}
            if(proposal)count++;
        }
        private MeasurementAccumulatedTotalsResponse totals(BigDecimal discount) {
            return MeasurementAccumulatedTotalsResponse.of(count,gross.subtract(priorGross),gross.subtract(discount).subtract(priorNet),weight.subtract(priorWeight));
        }
    }
}

