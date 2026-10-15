package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionState;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class MeasurementCompositionProgressService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService auth;
    private final MeasurementRepository measurements;
    private final MeasurementVersionRepository versions;
    private final MeasurementServiceRepository groups;
    private final MeasurementContractCompositionRepository compositions;
    private final MeasurementContinuityRepository continuity;
    private final MeasurementCompositionCoordinator coordinator;
    @Transactional(readOnly=true)
    public List<MeasurementCompositionState> get(UUID mid){
        auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.READ);return inspect(mid);
    }
    @Transactional(readOnly=true)
    public List<MeasurementCompositionState> inspect(UUID mid){
        UUID company=tenants.requireCurrentTenant().companyId();
        Measurement m=measurements.findByIdAndCompanyId(mid,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement"));
        List<MeasurementCompositionState> result=new ArrayList<>();
        for(MeasurementVersion v:versions.findByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(mid,company)){
            if(v.getStatus()!=MeasurementVersionStatus.ACCEPTED)continue;
            for(MeasurementService g:groups.findByMeasurementVersionIdAndCompanyId(v.getId(),company)){
                UUID sid=continuity.serviceForGroup(company,g.getId()).orElse(null);
                MeasurementContinuousService s=sid==null?null:continuity.findService(company,sid).orElse(null);
                MeasurementContractComposition c=compositions.findByMeasurementServiceIdAndCompanyId(g.getId(),company).orElse(null);
                UUID request=continuity.pendingCompositionRequest(company,g.getId()).orElse(null);
                String state=c!=null?(continuity.isReversed(company,c.getId())?"REVERSED":"APPLIED"):request!=null?"AWAITING_APPROVAL":
                    m.getContractId()==null?"NO_CONTRACT":s==null||s.destinationAction()==null?"NO_DESTINATION":"AUTHORIZATION_REQUIRED";
                result.add(new MeasurementCompositionState(sid,v.getId(),g.getId(),c==null?null:c.getId(),state,request));
            }
        }return List.copyOf(result);
    }
    @Transactional
    public List<MeasurementCompositionState> apply(UUID mid){
        auth.requirePermission(AccessModule.MEASUREMENTS,AccessAction.READ);
        if(auth.changeAuthorizationIfAllowed(AccessModule.SERVICES,ChangeOperation.CREATE).isEmpty()
                &&auth.changeAuthorizationIfAllowed(AccessModule.SERVICES,ChangeOperation.UPDATE).isEmpty())
            throw new org.springframework.security.access.AccessDeniedException("SERVICES change authorization is required");
        coordinator.coordinate(mid);return inspect(mid);
    }
}

