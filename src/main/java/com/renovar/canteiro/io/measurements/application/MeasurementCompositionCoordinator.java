package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.Comparator;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class MeasurementCompositionCoordinator {
    private final TenantContextHolder tenants;
    private final MeasurementRepository measurements;
    private final MeasurementVersionRepository versions;
    private final MeasurementServiceRepository groups;
    private final MeasurementContinuityRepository continuity;
    private final MeasurementContractCompositionRepository compositions;
    private final MeasurementContractCompositionService composer;
    private final com.renovar.canteiro.io.access.application.AccessAuthorizationService auth;
    @Transactional
    public void coordinate(UUID mid) {
        UUID company=tenants.requireCurrentTenant().companyId();
        continuity.lockMeasurement(company,mid);
        Measurement measurement=measurements.findByIdAndCompanyId(mid,company).orElseThrow(()->new TenantResourceNotFoundException("Measurement"));
        if(measurement.getContractId()==null||measurement.getStatus()==MeasurementStatus.CANCELLED) return;
        for(MeasurementVersion v:versions.findByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(mid,company).stream()
                .filter(t->t.getStatus()==MeasurementVersionStatus.ACCEPTED).sorted(Comparator.comparingInt(MeasurementVersion::getVersionNumber)).toList()){
            for(MeasurementService group:groups.findByMeasurementVersionIdAndCompanyId(v.getId(),company)){
                if(compositions.findByMeasurementServiceIdAndCompanyId(group.getId(),company).isPresent()) continue;
                MeasurementContinuousService s=continuity.serviceForGroup(company,group.getId()).flatMap(id->continuity.findService(company,id)).orElse(null);
                if(s==null||s.destinationAction()==null) continue;
                var operation = s.contractServiceId()==null ? com.renovar.canteiro.io.access.domain.ChangeOperation.CREATE
                        : com.renovar.canteiro.io.access.domain.ChangeOperation.UPDATE;
                if (auth.changeAuthorizationIfAllowed(com.renovar.canteiro.io.access.domain.AccessModule.SERVICES, operation).isPresent()) {
                    composer.compose(new ComposeMeasurementServiceCommand(mid,v.getId(),group.getId(),
                        s.contractServiceId()==null?MeasurementContractCompositionAction.CREATE_NEW_SERVICE:MeasurementContractCompositionAction.ADD_TO_EXISTING_SERVICE,
                        measurement.getContractId(),s.contractServiceId(),"Accepted increment with persisted destination"));
                }
            }
        }
    }
}

