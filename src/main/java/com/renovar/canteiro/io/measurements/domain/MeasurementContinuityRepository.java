package com.renovar.canteiro.io.measurements.domain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
public interface MeasurementContinuityRepository {
    void lockMeasurement(UUID companyId, UUID measurementId);
    MeasurementContinuousService save(MeasurementContinuousService service);
    Optional<MeasurementContinuousService> findService(UUID companyId, UUID id);
    List<MeasurementContinuousService> findServices(UUID companyId, UUID measurementId);
    void bindItem(UUID companyId, UUID measurementId, UUID versionId, UUID itemId, UUID serviceId);
    Optional<UUID> serviceForItem(UUID companyId, UUID itemId);
    default java.util.Map<UUID,UUID> serviceBindings(UUID companyId,List<UUID> itemIds) {
        java.util.Map<UUID,UUID> result=new java.util.HashMap<>();
        for(UUID id:itemIds)serviceForItem(companyId,id).ifPresent(service->result.put(id,service));
        return result;
    }
    void inheritItemBinding(UUID companyId, UUID sourceItemId, UUID itemId, UUID versionId);
    void bindGroup(UUID companyId, UUID groupId, UUID serviceId);
    Optional<UUID> serviceForGroup(UUID companyId, UUID groupId);
    void saveRecognition(MeasurementItemRecognition recognition);
    List<MeasurementItemRecognition> recognitionsForVersion(UUID companyId, UUID versionId);
    List<MeasurementItemRecognition> effectiveRecognitions(UUID companyId, UUID measurementId);
    List<MeasurementRecognitionTotals> effectiveTotals(UUID companyId, UUID measurementId);
    List<MeasurementItemRecognition> effectivePage(UUID companyId, UUID measurementId, UUID serviceId, int offset, int limit);
    List<UUID> pendingItemIdsPage(UUID companyId, UUID versionId, UUID serviceId, int offset, int limit);
    void clearReadContext();
    Optional<MeasurementItemRecognition> recognitionForItem(UUID companyId, UUID itemId);
    void reverseComposition(UUID companyId, UUID compositionId, UUID correctiveVersionId, BigDecimal amount, String reason, UUID actorId);
    boolean isReversed(UUID companyId, UUID compositionId);
    boolean hasUnreconciledReduction(UUID companyId, UUID compositionId);
    Optional<UUID> pendingCompositionRequest(UUID companyId, UUID groupId);
    void rememberCompositionRequest(UUID companyId, UUID groupId, UUID requestId);
}

