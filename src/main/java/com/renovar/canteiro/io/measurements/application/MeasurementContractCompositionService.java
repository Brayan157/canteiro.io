package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionOriginType;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRecognition;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class MeasurementContractCompositionService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final MeasurementRepository measurements;
    private final MeasurementVersionRepository versions;
    private final MeasurementServiceRepository groups;
    private final ContractRepository contracts;
    private final ContractServiceRepository contractServices;
    private final MeasurementContractCompositionRepository compositions;
    private final MeasurementContinuityRepository continuity;
    private final MeasurementOriginRecognitionService origins;
    private final BillingAllocationRepository allocations;
    private final MeasurementItemContractServiceConversionRepository legacyConversions;
    private final ChangeRequestService requests;
    private final ChangeRequestRepository requestRepository;
    private final AuditEventRecorder audit;
    private final com.renovar.canteiro.io.receivables.application.ValidateContractBillingLimitUseCase billingLimit;
    @Transactional(readOnly=true)
    public com.renovar.canteiro.io.shared.domain.CurrencyCode currency(UUID contractId) {
        return contracts.findByIdAndCompanyId(contractId,company()).orElseThrow(()->new TenantResourceNotFoundException("Contract")).getCurrency();
    }
    @Transactional(readOnly=true)
    public List<MeasurementService> listServices(UUID mid,UUID vid) {
        UUID company=company();auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.READ);
        measurement(company,mid);version(company,mid,vid);
        return groups.findByMeasurementVersionIdAndCompanyId(vid,company);
    }
    @Transactional
    public MeasurementContractCompositionResult compose(ComposeMeasurementServiceCommand command) {
        UUID company=company(); Context c=context(company,command);
        ChangeOperation operation=c.destination().contractServiceId()==null && command.action()==MeasurementContractCompositionAction.CREATE_NEW_SERVICE
                ? ChangeOperation.CREATE : ChangeOperation.UPDATE;
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.SERVICES,operation);
        Optional<MeasurementContractComposition> existing=compositions.findByMeasurementServiceIdAndCompanyId(c.group().getId(),company);
        if(existing.isPresent()) return new MeasurementContractCompositionResult(target(company,existing.get().getContractServiceId()),
            existing.get(),null,mode,true);
        Optional<UUID> pending=continuity.pendingCompositionRequest(company,c.group().getId());
        if(pending.isPresent()) return new MeasurementContractCompositionResult(null,null,
            requestRepository.findByIdAndCompanyId(pending.get(),company).orElseThrow(),ChangeAuthorizationMode.REQUEST_APPROVAL,false);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL) {
            Map<String,Object> proposal=proposal(command);
            proposal.put("destinationVersion",c.destination().lockVersion());
            ChangeRequest request=requests.create(new CreateChangeRequestCommand(AuditModule.SERVICES,
                operation==ChangeOperation.CREATE?ChangeRequestOperation.CREATE:ChangeRequestOperation.UPDATE,
                "MeasurementContractComposition",c.group().getId(),c.version().getLockVersion(),
                new ChangeRequestSnapshot(null,new AuditPayload(proposal)),command.justification()));
            continuity.rememberCompositionRequest(company,c.group().getId(),request.getId());
            return new MeasurementContractCompositionResult(null,null,request,mode,false);
        }
        return persist(company,command,"direct",null);
    }
    @Transactional
    public void applyApproved(ChangeRequest request) {
        Map<String,Object> p=request.getSnapshot().proposedData().values();
        ComposeMeasurementServiceCommand cmd=new ComposeMeasurementServiceCommand(id(p,"measurementId"),id(p,"measurementVersionId"),
            id(p,"measurementServiceId"),MeasurementContractCompositionAction.valueOf((String)p.get("action")),
            id(p,"contractId"),id(p,"contractServiceId"),request.getJustification());
        Context c=context(request.getCompanyId(),cmd);
        if(p.get("destinationVersion")!=null&&c.destination().lockVersion()!=((Number)p.get("destinationVersion")).intValue()
                && !(cmd.action()==MeasurementContractCompositionAction.CREATE_NEW_SERVICE
                && "CREATE_NEW_SERVICE".equals(c.destination().destinationAction()) && c.destination().contractServiceId()!=null))
            conflict("The destination changed after the composition proposal");
        persist(request.getCompanyId(),cmd,"approval",request.getId());
    }
    private MeasurementContractCompositionResult persist(UUID company,ComposeMeasurementServiceCommand cmd,String origin,UUID request) {
        Context c=context(company,cmd);
        Optional<MeasurementContractComposition> existing=compositions.findByMeasurementServiceIdAndCompanyId(c.group().getId(),company);
        if(existing.isPresent()) return new MeasurementContractCompositionResult(target(company,existing.get().getContractServiceId()),existing.get(),null,ChangeAuthorizationMode.DIRECT,true);
        List<MeasurementItemRecognition> rows=continuity.recognitionsForVersion(company,c.version().getId()).stream()
            .filter(r->r.continuousServiceId().equals(c.destination().id())).toList();
        if(rows.isEmpty()) conflict("The accepted service has no recognized origin");
        Set<UUID> roots=rows.stream().map(MeasurementItemRecognition::rootItemId).collect(java.util.stream.Collectors.toSet());
        for(MeasurementVersion historical:versions.findByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(c.measurement().getId(),company)) {
            if(legacyConversions.findByMeasurementVersionIdAndCompanyId(historical.getId(),company).stream()
                .anyMatch(conversion->roots.contains(origins.root(company,conversion.getMeasurementItemId()))))
                conflict("This origin was individually converted by the legacy workflow; reconcile it explicitly before continuous composition");
        }
        BigDecimal desiredGross=sum(rows,MeasurementItemRecognition::grossAmount),desiredNet=sum(rows,MeasurementItemRecognition::netAmount);
        List<MeasurementContractComposition> predecessors=compositions.findByMeasurementIdAndCompanyId(c.measurement().getId(),company).stream()
            .filter(previous->!previous.getMeasurementVersionId().equals(c.version().getId())&&!continuity.isReversed(company,previous.getId()))
            .filter(previous->previous.getMeasurementItemIds().stream().map(i->origins.root(company,i)).anyMatch(roots::contains)).toList();
        BigDecimal priorNet=predecessors.stream().map(MeasurementContractComposition::getNetAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal priorGross=predecessors.stream().map(MeasurementContractComposition::getGrossAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal priorWeight=predecessors.stream().map(MeasurementContractComposition::getTotalWeightKg).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal netIncrement=desiredNet.subtract(priorNet);
        if(netIncrement.signum()<0) {
            for(MeasurementContractComposition previous:predecessors) {
                if(allocations.findByMeasurementContractCompositionIdAndCompanyId(previous.getId(),company).stream().anyMatch(a->a.getStatus()==BillingAllocationStatus.ACTIVE))
                    conflict("Reverse the active billing before reducing a composition");
                ContractService oldTarget=target(company,previous.getContractServiceId());
                contractServices.save(oldTarget.withReversedMeasurementComposition(previous.getNetAmount()));
                continuity.reverseComposition(company,previous.getId(),c.version().getId(),previous.getNetAmount(),
                    cmd.justification()==null?"Accepted corrective revision":cmd.justification(),tenants.requireCurrentTenant().userId());
                audit.recordDirectAction(AuditModule.SERVICES,AuditAction.CANCEL,"MeasurementCompositionReversal",previous.getId(),null,
                    Map.of("sourceCompositionId",previous.getId().toString(),"correctiveVersionId",c.version().getId().toString(),
                        "netAmount",previous.getNetAmount().negate().toPlainString()),metadata(origin,request));
            }
            netIncrement=desiredNet; priorGross=BigDecimal.ZERO; priorWeight=BigDecimal.ZERO;
        }
        ContractService target;
        if(c.destination().contractServiceId()!=null) target=target(company,c.destination().contractServiceId());
        else if(cmd.action()==MeasurementContractCompositionAction.ADD_TO_EXISTING_SERVICE) target=target(company,cmd.contractServiceId());
        else target=contractServices.save(ContractService.create(company,c.contract().getId(),c.destination().serviceTitle(),
            "Accepted measurement "+c.measurement().getTitle(),ContractServiceStatus.ACTIVE,BigDecimal.ONE,BigDecimal.ZERO));
        if(!c.contract().getId().equals(target.getContractId())||target.getStatus()!=ContractServiceStatus.ACTIVE)
            conflict("The destination service must be active in the same contract");
        BigDecimal gross=desiredGross.subtract(priorGross).max(BigDecimal.ZERO).setScale(2),net=netIncrement.setScale(2);
        BigDecimal weight=rows.stream().map(MeasurementItemRecognition::totalWeightKg).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add).subtract(priorWeight);
        boolean hasWeight=rows.stream().anyMatch(r->r.totalWeightKg()!=null)||predecessors.stream().anyMatch(p->p.getTotalWeightKg()!=null);
        ContractService updated=contractServices.save(target.withAddedMeasurementComposition(net));
        MeasurementContractComposition composition=compositions.save(MeasurementContractComposition.create(company,c.measurement().getId(),
            c.version().getId(),c.group().getId(),c.contract().getId(),updated.getId(),
            c.contract().getPricingMode()==ContractPricingMode.MEASUREMENT_BASED?MeasurementCompositionOriginType.MEASUREMENT_PRICING:MeasurementCompositionOriginType.CONTRACT_ADDITION,
            gross,net,hasWeight?weight:null,rows.stream().map(MeasurementItemRecognition::measurementItemId).toList(),cmd.justification(),tenants.requireCurrentTenant().userId()));
        if(c.destination().contractServiceId()==null) continuity.save(c.destination().destination(
            c.destination().destinationAction()==null?cmd.action().name():c.destination().destinationAction(),updated.getId()));
        billingLimit.requireAvailableForActivation(company,c.contract().getId(),List.of());
        audit.recordDirectAction(AuditModule.SERVICES,AuditAction.CREATE,"MeasurementContractComposition",composition.getId(),null,
            Map.of("measurementId",c.measurement().getId().toString(),"measurementVersionId",c.version().getId().toString(),
                "measurementServiceId",c.group().getId().toString(),"continuousServiceId",c.destination().id().toString(),
                "contractServiceId",updated.getId().toString(),"netAmount",net.toPlainString()),metadata(origin,request));
        return new MeasurementContractCompositionResult(updated,composition,null,ChangeAuthorizationMode.DIRECT,false);
    }
    private Context context(UUID company,ComposeMeasurementServiceCommand cmd) {
        if(cmd==null||cmd.measurementId()==null||cmd.measurementVersionId()==null||cmd.measurementServiceId()==null||cmd.contractId()==null||cmd.action()==null)
            throw new IllegalArgumentException("Measurement, accepted version, group and compatible contract are required");
        continuity.lockMeasurement(company,cmd.measurementId());
        Measurement m=measurement(company,cmd.measurementId());
        MeasurementVersion v=version(company,m.getId(),cmd.measurementVersionId());
        if(v.getStatus()!=MeasurementVersionStatus.ACCEPTED) conflict("Only ACCEPTED origins can compose a contract");
        if(m.getContractId()==null||m.getWorkId()==null||!m.getContractId().equals(cmd.contractId())) conflict("Link the measurement to the selected contract first");
        Contract contract=contracts.findByIdAndCompanyIdWithBillingLock(cmd.contractId(),company).orElseThrow(()->new TenantResourceNotFoundException("Contract"));
        m.getCurrency().requireSame(contract.getCurrency());
        if(!m.getWorkId().equals(contract.getWorkId())) conflict("The measurement and contract must have the same work");
        MeasurementService group=groups.findByIdAndCompanyId(cmd.measurementServiceId(),company)
            .filter(g->v.getId().equals(g.getMeasurementVersionId())).orElseThrow(()->new TenantResourceNotFoundException("Measurement service group"));
        UUID sid=continuity.serviceForGroup(company,group.getId()).orElseThrow(()->new IllegalStateException("Measurement group lacks a continuous origin"));
        MeasurementContinuousService destination=continuity.findService(company,sid).orElseThrow();
        if(cmd.action()==MeasurementContractCompositionAction.ADD_TO_EXISTING_SERVICE&&cmd.contractServiceId()==null)
            throw new IllegalArgumentException("An existing destination requires contractServiceId");
        if(cmd.action()==MeasurementContractCompositionAction.CREATE_NEW_SERVICE&&cmd.contractServiceId()!=null)
            throw new IllegalArgumentException("A new destination must not provide contractServiceId");
        if(destination.contractServiceId()!=null&&cmd.contractServiceId()!=null&&!destination.contractServiceId().equals(cmd.contractServiceId()))
            conflict("The accepted service is already associated with another destination");
        if(cmd.contractServiceId()!=null) {
            ContractService t=target(company,cmd.contractServiceId());
            if(!contract.getId().equals(t.getContractId())) throw new TenantResourceNotFoundException("Contract service");
            if(t.getStatus()!=ContractServiceStatus.ACTIVE) conflict("The destination service is not active");
        }
        return new Context(m,v,group,contract,destination);
    }
    private Measurement measurement(UUID company,UUID id){return measurements.findByIdAndCompanyId(id,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement"));}
    private MeasurementVersion version(UUID company,UUID mid,UUID id){return versions.findByIdAndCompanyId(id,company).filter(v->mid.equals(v.getMeasurementId())).orElseThrow(()->new TenantResourceNotFoundException("Measurement version"));}
    private ContractService target(UUID company,UUID id){return contractServices.findByIdAndCompanyId(id,company).orElseThrow(()->new TenantResourceNotFoundException("Contract service"));}
    private Map<String,Object> proposal(ComposeMeasurementServiceCommand cmd){Map<String,Object> p=new HashMap<>();p.put("measurementId",cmd.measurementId().toString());p.put("measurementVersionId",cmd.measurementVersionId().toString());p.put("measurementServiceId",cmd.measurementServiceId().toString());p.put("contractId",cmd.contractId().toString());p.put("contractServiceId",cmd.contractServiceId()==null?null:cmd.contractServiceId().toString());p.put("action",cmd.action().name());return p;}
    private UUID id(Map<String,Object> p,String key){return p.get(key)==null?null:UUID.fromString(p.get(key).toString());}
    private BigDecimal sum(List<MeasurementItemRecognition> rows,java.util.function.Function<MeasurementItemRecognition,BigDecimal> f){return rows.stream().map(f).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);}
    private UUID company(){return tenants.requireCurrentTenant().companyId();}
    private Map<String,Object> metadata(String origin,UUID id){return id==null?Map.of("origin",origin):Map.of("origin",origin,"changeRequestId",id.toString());}
    private void conflict(String message){throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,message);}
    private record Context(Measurement measurement,MeasurementVersion version,MeasurementService group,Contract contract,MeasurementContinuousService destination){}
}
