package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementDocumentType;
import java.util.UUID;

public record UploadMeasurementDocumentCommand(UUID measurementId, UUID measurementVersionId,
                                               MeasurementDocumentType documentType, String filename,
                                               String contentType, byte[] content) {
}
