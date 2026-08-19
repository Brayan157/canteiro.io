package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementDocumentType;
import java.time.Instant;
import java.util.UUID;

public record MeasurementDocumentResponse(UUID id, UUID measurementId, UUID measurementVersionId,
                                          MeasurementDocumentType documentType, String originalFilename,
                                          String contentType, long contentSize, String sha256, Instant createdAt) {
}
