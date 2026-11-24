package com.renovar.canteiro.io.workdiary.api;

import com.renovar.canteiro.io.workdiary.application.DiaryChange;
import com.renovar.canteiro.io.workdiary.application.ReadDiaryOperationsUseCase;
import com.renovar.canteiro.io.workdiary.application.WorkDiaryService;
import com.renovar.canteiro.io.workdiary.domain.DiaryRevisionSummary;
import com.renovar.canteiro.io.workdiary.domain.WorkDiaryRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/works/{workId}/diary")
public class WorkDiaryController {
    private final WorkDiaryService diaries;
    private final ReadDiaryOperationsUseCase operations;

    public WorkDiaryController(WorkDiaryService diaries, ReadDiaryOperationsUseCase operations) {
        this.diaries = diaries;
        this.operations = operations;
    }

    @GetMapping("/operations")
    @Operation(operationId = "listEffectiveWorkDiaryOperations", summary = "Read the current user's effective operations for the work")
    public ReadDiaryOperationsUseCase.OperationsResponse operations(@PathVariable UUID workId) {
        return operations.read(workId);
    }

    @PutMapping("/time-zone")
    @Operation(operationId = "configureWorkDiaryTimeZone", summary = "Configure the work time zone before the first diary")
    public ZoneResponse setTimeZone(@PathVariable UUID workId, @Valid @RequestBody ZoneUpdateRequest request) {
        return new ZoneResponse(diaries.configureZone(workId, request.timeZone()));
    }

    @GetMapping("/time-zone")
    @Operation(operationId = "getWorkDiaryTimeZone", summary = "Read the authorized work time zone")
    public ZoneResponse timeZone(@PathVariable UUID workId) {
        return new ZoneResponse(diaries.zone(workId));
    }

    @GetMapping("/{date}/employees")
    @Operation(operationId = "listWorkDiaryEmployees", summary = "List eligible employees with a minimal projection for the date")
    public List<WorkDiaryRepository.EmployeeSelection> employees(
            @PathVariable UUID workId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return diaries.employees(workId, date, offset, limit);
    }

    @GetMapping("/time-summary")
    @Operation(operationId = "summarizeWorkDiaryTime", summary = "Sum effective minutes by employee and service for an inclusive period")
    public List<WorkDiaryRepository.TimeSummary> timeSummary(
            @PathVariable UUID workId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return diaries.summary(workId, from, through, offset, limit);
    }

    @GetMapping("/audit")
    @Operation(operationId = "listWorkDiaryAudit", summary = "Read the scoped audit trail for the authorized work")
    public List<WorkDiaryRepository.ScopedAuditEvent> audit(
            @PathVariable UUID workId, @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return diaries.audit(workId, offset, limit);
    }

    @GetMapping("/{date}")
    @Operation(operationId = "getWorkDiary", summary = "Read the effective diary for the work date")
    public WorkDiaryResponse read(@PathVariable UUID workId,
                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return WorkDiaryResponse.from(diaries.read(workId, date));
    }

    @GetMapping("/{date}/revisions")
    @Operation(operationId = "listWorkDiaryRevisions", summary = "List historical revisions before a revision cursor")
    public List<DiaryRevisionSummary> revisions(@PathVariable UUID workId,
                                               @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                               @RequestParam(required = false) @Min(0) Long before,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return diaries.history(workId, date, before == null ? Long.MAX_VALUE : before, limit);
    }

    @GetMapping("/{date}/revisions/{revision}")
    @Operation(operationId = "getWorkDiaryRevision", summary = "Read an immutable diary revision")
    public WorkDiaryResponse revision(@PathVariable UUID workId,
                                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @PathVariable @Min(0) long revision) {
        return WorkDiaryResponse.from(diaries.revision(workId, date, revision));
    }

    @PostMapping("/{date}")
    @Operation(operationId = "createWorkDiary", summary = "Create or request a diary for the work date")
    @ApiResponse(responseCode = "200", description = "Previous operation result")
    @ApiResponse(responseCode = "201", description = "Diary created")
    @ApiResponse(responseCode = "202", description = "Pending change request")
    public ResponseEntity<ChangeResponse> create(@PathVariable UUID workId,
                                                 @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @Valid @RequestBody DiaryChange request) {
        return change(workId, date, WorkOperation.CREATE, request);
    }

    @PutMapping("/{date}")
    @Operation(operationId = "correctWorkDiary", summary = "Replace content with an audited versioned revision")
    @ApiResponse(responseCode = "200", description = "Diary revision applied or previous operation returned")
    @ApiResponse(responseCode = "202", description = "Pending change request")
    public ResponseEntity<ChangeResponse> correct(@PathVariable UUID workId,
                                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @Valid @RequestBody DiaryChange request) {
        return change(workId, date, WorkOperation.UPDATE, request);
    }

    @PostMapping("/{date}/close")
    @Operation(operationId = "closeWorkDiary", summary = "Freeze the current revision for review")
    @ApiResponse(responseCode = "200", description = "Diary closed or previous operation returned")
    @ApiResponse(responseCode = "202", description = "Pending change request")
    public ResponseEntity<ChangeResponse> close(@PathVariable UUID workId,
                                                 @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @Valid @RequestBody DiaryChange request) {
        return change(workId, date, WorkOperation.CLOSE, request);
    }

    @PostMapping("/{date}/reopen")
    @Operation(operationId = "reopenWorkDiary", summary = "Reopen the diary with a reason and preserve the closed revision")
    @ApiResponse(responseCode = "200", description = "Diary reopened or previous operation returned")
    @ApiResponse(responseCode = "202", description = "Pending change request")
    public ResponseEntity<ChangeResponse> reopen(@PathVariable UUID workId,
                                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @Valid @RequestBody DiaryChange request) {
        return change(workId, date, WorkOperation.REOPEN, request);
    }

    private ResponseEntity<ChangeResponse> change(UUID workId, LocalDate date, WorkOperation operation, DiaryChange request) {
        var result = diaries.change(new DiaryChange.Command(workId, date, operation, request));
        var receipt = result.receipt();
        HttpStatus status = HttpStatus.OK;
        if (result.status() == ChangeRequestStatus.PENDING) {
            status = HttpStatus.ACCEPTED;
        } else if (!result.replay() && operation == WorkOperation.CREATE) {
            status = HttpStatus.CREATED;
        }
        return ResponseEntity.status(status).body(new ChangeResponse(receipt.diaryId(), receipt.appliedRevision(),
                receipt.changeRequestId(), result.status(), result.replay()));
    }

    public record ChangeResponse(UUID diaryId, Long revision, UUID changeRequestId,
                                 ChangeRequestStatus status, boolean replay) {
    }

    public record ZoneUpdateRequest(@NotBlank @Size(max = 100) String timeZone) {
    }

    public record ZoneResponse(String timeZone) {
    }
}
