package com.renovar.canteiro.io.workdiary.api;

import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.workdiary.application.DiaryPlanService;
import com.renovar.canteiro.io.workdiary.application.DiaryPlanUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/works/{workId}/diary/{date}")
public class DiaryPlanController {
    private final DiaryPlanService plans;

    public DiaryPlanController(DiaryPlanService plans) {
        this.plans = plans;
    }

    @PutMapping("/plan")
    @Operation(operationId = "updateWorkDiaryPlan", summary = "Publish or request a daily plan revision")
    @ApiResponse(responseCode = "200", description = "Plan applied or previous operation returned")
    @ApiResponse(responseCode = "202", description = "Plan revision pending approval")
    public ResponseEntity<DiaryPlanService.PlanChangeResponse> update(
            @PathVariable UUID workId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DiaryPlanUpdateRequest request) {
        var result = plans.change(workId, date, request);
        return ResponseEntity.status(result.status() == ChangeRequestStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result);
    }

    @GetMapping("/comparison")
    @Operation(operationId = "compareWorkDiaryPlan", summary = "Compare an official plan version and an effective diary revision")
    public DiaryPlanService.ComparisonResponse compare(
            @PathVariable UUID workId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive Long planVersion,
            @RequestParam(required = false) @PositiveOrZero Long diaryRevision) {
        return plans.read(workId, date, planVersion, diaryRevision);
    }
}
