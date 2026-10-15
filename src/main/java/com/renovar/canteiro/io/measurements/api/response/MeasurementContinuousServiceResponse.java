package com.renovar.canteiro.io.measurements.api.response;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import java.time.Instant;
import java.util.UUID;
public record MeasurementContinuousServiceResponse(UUID id,UUID measurementId,String serviceTitle,int sortOrder,
        String destinationAction,UUID contractServiceId,Integer lockVersion,Instant createdAt,Instant updatedAt,
        UUID changeRequestId,ChangeAuthorizationMode authorizationMode) {
    public static MeasurementContinuousServiceResponse from(MeasurementContinuousService s) {
        return new MeasurementContinuousServiceResponse(s.id(),s.measurementId(),s.serviceTitle(),s.sortOrder(),
            s.destinationAction(),s.contractServiceId(),s.lockVersion(),s.createdAt(),s.updatedAt(),null,null);
    }
    public static MeasurementContinuousServiceResponse from(com.renovar.canteiro.io.measurements.application.MeasurementContinuousServiceChangeResult r) {
        if(r.changeRequest()!=null) return new MeasurementContinuousServiceResponse(null,null,null,0,null,null,null,null,null,
            r.changeRequest().getId(),r.authorizationMode());
        MeasurementContinuousService s=r.service();
        return new MeasurementContinuousServiceResponse(s.id(),s.measurementId(),s.serviceTitle(),s.sortOrder(),s.destinationAction(),
            s.contractServiceId(),s.lockVersion(),s.createdAt(),s.updatedAt(),null,r.authorizationMode());
    }
}

