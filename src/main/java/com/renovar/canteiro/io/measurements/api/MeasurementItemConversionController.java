package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.response.MeasurementItemConversionResponse;
import com.renovar.canteiro.io.measurements.application.ConvertAcceptedMeasurementItemCommand;
import com.renovar.canteiro.io.measurements.application.ConvertAcceptedMeasurementItemToContractServiceUseCase;
import com.renovar.canteiro.io.measurements.application.MeasurementItemConversionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementItemConversionController {

    private final ConvertAcceptedMeasurementItemToContractServiceUseCase conversionUseCase;

    @PostMapping(path = "/{measurementId}/versions/{versionId}/items/{itemId}/contract-service",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Converts an accepted measurement item into a contract service exactly once")
    public MeasurementItemConversionResponse convert(@PathVariable UUID measurementId, @PathVariable UUID versionId,
                                                      @PathVariable UUID itemId,
                                                      @RequestParam(required = false) String justification) {
        MeasurementItemConversionResult result = conversionUseCase.convert(new ConvertAcceptedMeasurementItemCommand(
                measurementId, versionId, itemId, justification
        ));
        return new MeasurementItemConversionResponse(
                result.contractService() == null ? null : result.contractService().getId(),
                result.changeRequest() == null ? null : result.changeRequest().getId(),
                result.authorizationMode(),
                result.alreadyConverted()
        );
    }
}
