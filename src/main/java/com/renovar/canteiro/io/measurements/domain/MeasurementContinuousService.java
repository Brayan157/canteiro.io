package com.renovar.canteiro.io.measurements.domain;
import java.time.Instant;
import java.util.UUID;
public record MeasurementContinuousService(UUID id, UUID companyId, UUID measurementId, String serviceTitle,
        int sortOrder, String destinationAction, UUID contractServiceId, int lockVersion, Instant createdAt, Instant updatedAt) {
    public MeasurementContinuousService {
        if (companyId == null || measurementId == null || serviceTitle == null || serviceTitle.isBlank() || serviceTitle.trim().length() > 255)
            throw new IllegalArgumentException("Measurement and a service title of 1 to 255 characters are required");
        serviceTitle = serviceTitle.trim();
        if (sortOrder < 1 || lockVersion < 0) throw new IllegalArgumentException("Invalid service order or version");
        if (destinationAction != null && !destinationAction.equals("CREATE_NEW_SERVICE") && !destinationAction.equals("ADD_TO_EXISTING_SERVICE"))
            throw new IllegalArgumentException("Invalid service destination action");
        if ("ADD_TO_EXISTING_SERVICE".equals(destinationAction) && contractServiceId == null)
            throw new IllegalArgumentException("An existing destination requires contractServiceId");
    }
    public MeasurementContinuousService destination(String action, UUID target) {
        return new MeasurementContinuousService(id, companyId, measurementId, serviceTitle, sortOrder, action, target, lockVersion, createdAt, updatedAt);
    }
}
