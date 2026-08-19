package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.response.MeasurementFinancialStatusResponse;
import com.renovar.canteiro.io.measurements.application.GetMeasurementFinancialStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementFinancialStatusController {

    private final GetMeasurementFinancialStatusUseCase financialStatusUseCase;

    @GetMapping(path = "/{measurementId}/versions/{versionId}/financial-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Shows billed amount and balance only from services originated by the measurement version")
    public MeasurementFinancialStatusResponse get(@PathVariable UUID measurementId, @PathVariable UUID versionId) {
        return MeasurementFinancialStatusResponse.from(financialStatusUseCase.get(measurementId, versionId));
    }
}
