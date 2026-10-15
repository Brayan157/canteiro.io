package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
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
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class MeasurementContinuousServiceManagementService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final MeasurementRepository measurements;
    private final MeasurementVersionRepository versions;
    private final MeasurementItemRepository items;
    private final ContractServiceRepository contractServices;
    private final MeasurementContinuityRepository continuity;
    private final ChangeRequestService requests;
    private final AuditEventRecorder audit;
    private final ObjectProvider<MeasurementCompositionCoordinator> coordinator;
    @Transactional(readOnly=true)
    public List<MeasurementContinuousService> list(UUID mid) {
        auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.READ);
        UUID company=company(); measurement(company,mid);
        return continuity.findServices(company,mid);
    }
    @Transactional
    public MeasurementContinuousServiceChangeResult create(UUID mid,String title,String action,UUID target,String justification) {
        UUID company=company(); continuity.lockMeasurement(company,mid);
        Measurement m=measurement(company,mid); writable(m); validateDestination(company,m,action,target);
        MeasurementContinuousService candidate=new MeasurementContinuousService(null,company,mid,title,
            continuity.findServices(company,mid).stream().mapToInt(MeasurementContinuousService::sortOrder).max().orElse(0)+1,
            action,target,0,null,null);
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.MEASUREMENTS,ChangeOperation.CREATE);
        Map<String,Object> proposal=proposal(mid,null,title,action,target,0,null);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL) return pending("CREATE",null,0,proposal,justification,mode);
        return persistCreate(candidate,"direct",null);
    }
    @Transactional
    public MeasurementContinuousServiceChangeResult destination(UUID mid,UUID sid,int expectedVersion,String action,UUID target,String justification) {
        UUID company=company(); continuity.lockMeasurement(company,mid);
        Measurement m=measurement(company,mid); writable(m);
        MeasurementContinuousService existing=service(company,mid,sid);
        if(existing.lockVersion()!=expectedVersion) conflict("Service destination changed; reload the measurement");
        validateDestination(company,m,action,target);
        immutableDestination(company,mid,existing,action,target);
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.MEASUREMENTS,ChangeOperation.UPDATE);
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL)
            return pending("UPDATE",sid,expectedVersion,proposal(mid,sid,existing.serviceTitle(),action,target,expectedVersion,null),justification,mode);
        return persistDestination(existing.destination(action,target),"direct",null);
    }
    @Transactional
    public MeasurementContinuousServiceChangeResult assign(UUID mid,UUID versionId,UUID itemId,UUID sid,String justification) {
        UUID company=company(); continuity.lockMeasurement(company,mid);
        draft(company,mid,versionId);
        MeasurementItem item=items.findByIdAndCompanyId(itemId,company).filter(i->versionId.equals(i.getMeasurementVersionId())&&i.isActive())
            .orElseThrow(()->new TenantResourceNotFoundException("Measurement item"));
        MeasurementContinuousService s=service(company,mid,sid);
        if(item.getSourceItemId()!=null && continuity.recognitionForItem(company,item.getSourceItemId()).isPresent()
                && !continuity.serviceForItem(company,item.getSourceItemId()).orElseThrow().equals(sid))
            conflict("An accepted source cannot be reassociated by an ordinary draft operation");
        ChangeAuthorizationMode mode=auth.requireChangeAuthorization(AccessModule.MEASUREMENTS,ChangeOperation.UPDATE);
        Map<String,Object> p=proposal(mid,sid,s.serviceTitle(),s.destinationAction(),s.contractServiceId(),s.lockVersion(),itemId);
        p.put("previousServiceId",continuity.serviceForItem(company,itemId).map(UUID::toString).orElse(null));
        p.put("versionId",versionId.toString());
        if(mode==ChangeAuthorizationMode.REQUEST_APPROVAL) return pending("ASSIGN",itemId,0,p,justification,mode);
        return persistAssign(company,p,"direct",null);
    }
    @Transactional
    public void applyApproved(ChangeRequest request) {
        Map<String,Object> p=request.getSnapshot().proposedData().values();
        UUID company=request.getCompanyId(),mid=id(p,"measurementId"); continuity.lockMeasurement(company,mid);
        writable(measurement(company,mid));
        String kind=(String)p.get("kind");
        if("CREATE".equals(kind)) {
            validateDestination(company,measurement(company,mid),(String)p.get("action"),id(p,"targetId"));
            persistCreate(new MeasurementContinuousService(null,company,mid,(String)p.get("title"),
                continuity.findServices(company,mid).stream().mapToInt(MeasurementContinuousService::sortOrder).max().orElse(0)+1,
                (String)p.get("action"),id(p,"targetId"),0,null,null),"approval",request.getId());
        } else if("ASSIGN".equals(kind)) {
            draft(company,mid,id(p,"versionId")); service(company,mid,id(p,"serviceId"));
            if(!Objects.equals(id(p,"previousServiceId"),continuity.serviceForItem(company,id(p,"itemId")).orElse(null)))
                conflict("The item service changed after the assignment was submitted");
            MeasurementItem item=items.findByIdAndCompanyId(id(p,"itemId"),company).filter(i->i.isActive()&&id(p,"versionId").equals(i.getMeasurementVersionId())).orElseThrow();
            if(item.getSourceItemId()!=null&&continuity.recognitionForItem(company,item.getSourceItemId()).isPresent()
                    &&!continuity.serviceForItem(company,item.getSourceItemId()).orElseThrow().equals(id(p,"serviceId")))
                conflict("Accepted origin reassociation requires a formal operation");
            persistAssign(company,p,"approval",request.getId());
        } else {
            MeasurementContinuousService old=service(company,mid,id(p,"serviceId"));
            if(old.lockVersion()!=((Number)p.get("expectedVersion")).intValue()) conflict("Service destination changed after submission");
            validateDestination(company,measurement(company,mid),(String)p.get("action"),id(p,"targetId"));
            immutableDestination(company,mid,old,(String)p.get("action"),id(p,"targetId"));
            persistDestination(old.destination((String)p.get("action"),id(p,"targetId")),"approval",request.getId());
        }
    }
    public void validateItemService(UUID company,UUID mid,UUID sid) {
        if(sid!=null) service(company,mid,sid);
    }
    public void bindNewItem(UUID company,UUID mid,UUID versionId,UUID itemId,UUID sid) {
        if(sid!=null) { service(company,mid,sid); continuity.bindItem(company,mid,versionId,itemId,sid); }
    }
    private MeasurementContinuousServiceChangeResult persistCreate(MeasurementContinuousService candidate,String origin,UUID request) {
        MeasurementContinuousService saved=continuity.save(candidate);
        audit.recordDirectAction(AuditModule.MEASUREMENTS,AuditAction.CREATE,"MeasurementContinuousService",saved.id(),null,data(saved),metadata(origin,request));
        coordinator.getObject().coordinate(saved.measurementId());
        return new MeasurementContinuousServiceChangeResult(continuity.findService(saved.companyId(),saved.id()).orElseThrow(),null,ChangeAuthorizationMode.DIRECT);
    }
    private MeasurementContinuousServiceChangeResult persistDestination(MeasurementContinuousService candidate,String origin,UUID request) {
        MeasurementContinuousService before=continuity.findService(candidate.companyId(),candidate.id()).orElseThrow();
        MeasurementContinuousService saved=continuity.save(candidate);
        audit.recordDirectAction(AuditModule.MEASUREMENTS,AuditAction.UPDATE,"MeasurementServiceDestination",saved.id(),data(before),data(saved),metadata(origin,request));
        coordinator.getObject().coordinate(saved.measurementId());
        return new MeasurementContinuousServiceChangeResult(continuity.findService(saved.companyId(),saved.id()).orElseThrow(),null,ChangeAuthorizationMode.DIRECT);
    }
    private MeasurementContinuousServiceChangeResult persistAssign(UUID company,Map<String,Object> p,String origin,UUID request) {
        UUID mid=id(p,"measurementId"),sid=id(p,"serviceId"),item=id(p,"itemId");
        continuity.bindItem(company,mid,id(p,"versionId"),item,sid);
        versions.save(versions.findByIdAndCompanyId(id(p,"versionId"),company).orElseThrow());
        audit.recordDirectAction(AuditModule.MEASUREMENTS,AuditAction.UPDATE,"MeasurementItemService",item,null,
            Map.of("measurementId",mid.toString(),"continuousServiceId",sid.toString()),metadata(origin,request));
        return new MeasurementContinuousServiceChangeResult(service(company,mid,sid),null,ChangeAuthorizationMode.DIRECT);
    }
    private MeasurementContinuousServiceChangeResult pending(String kind,UUID entity,int version,Map<String,Object> proposal,String why,ChangeAuthorizationMode mode) {
        proposal.put("kind",kind);
        ChangeRequest request=requests.create(new CreateChangeRequestCommand(AuditModule.MEASUREMENTS,
            "CREATE".equals(kind)?ChangeRequestOperation.CREATE:ChangeRequestOperation.UPDATE,"MeasurementContinuousService",
            entity,version,new ChangeRequestSnapshot(null,new AuditPayload(proposal)),why));
        return new MeasurementContinuousServiceChangeResult(null,request,mode);
    }
    private void immutableDestination(UUID company,UUID mid,MeasurementContinuousService old,String action,UUID target) {
        boolean accepted=continuity.effectiveRecognitions(company,mid).stream().anyMatch(r->r.continuousServiceId().equals(old.id()));
        if(accepted && old.destinationAction()!=null && (!Objects.equals(action,old.destinationAction())||!Objects.equals(target,old.contractServiceId())))
            conflict("An accepted service destination cannot be replaced by an ordinary update");
    }
    private void validateDestination(UUID company,Measurement m,String action,UUID target) {
        if(action==null) { if(target!=null) throw new IllegalArgumentException("Destination action is required"); return; }
        if(!action.equals("CREATE_NEW_SERVICE")&&!action.equals("ADD_TO_EXISTING_SERVICE")) throw new IllegalArgumentException("Invalid destination action");
        if(action.equals("CREATE_NEW_SERVICE")) { if(target!=null) throw new IllegalArgumentException("A new destination must not specify contractServiceId"); return; }
        if(m.getContractId()==null) conflict("Link a contract before selecting an existing contract service");
        auth.requirePermission(AccessModule.SERVICES,AccessAction.READ);
        ContractService s=contractServices.findByIdAndCompanyId(target,company)
            .filter(t->m.getContractId().equals(t.getContractId())).orElseThrow(()->new TenantResourceNotFoundException("Contract service"));
        if(s.getStatus()!=ContractServiceStatus.ACTIVE) conflict("The selected contract service is not active");
    }
    private void draft(UUID company,UUID mid,UUID versionId) {
        Measurement m=measurement(company,mid); writable(m);
        MeasurementVersion v=versions.findByIdAndCompanyId(versionId,company).filter(t->mid.equals(t.getMeasurementId())).orElseThrow(()->new TenantResourceNotFoundException("Measurement version"));
        if(v.getStatus()!=MeasurementVersionStatus.DRAFT||!versions.findLatestByMeasurementIdAndCompanyId(mid,company).orElseThrow().getId().equals(versionId))
            conflict("Only the current DRAFT version is editable");
    }
    private void writable(Measurement m) { if(m.getStatus()==MeasurementStatus.CANCELLED||m.getStatus()==MeasurementStatus.FINALIZED) conflict("The measurement is closed"); }
    private UUID company() { return tenants.requireCurrentTenant().companyId(); }
    private Measurement measurement(UUID company,UUID mid) { return measurements.findByIdAndCompanyId(mid,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement")); }
    private MeasurementContinuousService service(UUID company,UUID mid,UUID sid) { return continuity.findService(company,sid).filter(s->mid.equals(s.measurementId())).orElseThrow(()->new TenantResourceNotFoundException("Measurement continuous service")); }
    private Map<String,Object> proposal(UUID mid,UUID sid,String title,String action,UUID target,int version,UUID item) {
        Map<String,Object> p=new HashMap<>(); p.put("measurementId",mid.toString());p.put("serviceId",string(sid));p.put("title",title);
        p.put("action",action);p.put("targetId",string(target));p.put("expectedVersion",version);p.put("itemId",string(item));return p;
    }
    private Map<String,Object> data(MeasurementContinuousService s) { Map<String,Object> d=new HashMap<>();d.put("measurementId",s.measurementId().toString());d.put("serviceTitle",s.serviceTitle());d.put("destinationAction",s.destinationAction());d.put("contractServiceId",string(s.contractServiceId()));return d; }
    private Map<String,Object> metadata(String origin,UUID id) { return id==null?Map.of("origin",origin):Map.of("origin",origin,"changeRequestId",id.toString()); }
    private UUID id(Map<String,Object> p,String key) { return p.get(key)==null?null:UUID.fromString(p.get(key).toString()); }
    private String string(UUID id) { return id==null?null:id.toString(); }
    private void conflict(String message) { throw new ApiException(HttpStatus.CONFLICT,ErrorCode.BUSINESS_RULE_VIOLATION,message); }
}

