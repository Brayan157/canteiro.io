package com.renovar.canteiro.io.works.api;

import com.renovar.canteiro.io.works.api.request.CreateWorkRequest;
import com.renovar.canteiro.io.works.api.request.UpdateWorkRequest;
import com.renovar.canteiro.io.works.api.response.WorkChangeResponse;
import com.renovar.canteiro.io.works.api.response.WorkResponse;
import com.renovar.canteiro.io.works.application.CreateWorkCommand;
import com.renovar.canteiro.io.works.application.WorkManagementService;
import com.renovar.canteiro.io.works.application.WorkFilter;
import com.renovar.canteiro.io.works.application.UpdateWorkCommand;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/works")
@RequiredArgsConstructor
@Tag(name = "Works")
public class WorkController {

    private final WorkManagementService workManagementService;

    private static final Set<String> SORT_FIELDS = Set.of("name", "reference", "status", "executionLocationType",
            "startedOn", "expectedCompletionOn", "completedOn", "createdAt", "updatedAt");

    @GetMapping
    @Operation(summary = "Lists works in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated work list", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<WorkResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) UUID finalCustomerId,
            @RequestParam(required = false) String finalCustomerName,
            @RequestParam(required = false) String finalCustomerDocument,
            @RequestParam(required = false) WorkStatus status,
            @RequestParam(required = false) WorkExecutionLocationType executionLocationType,
            @RequestParam(required = false) java.time.LocalDate startedFrom,
            @RequestParam(required = false) java.time.LocalDate startedTo,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionFrom,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionTo
    ) {
        return PageResponse.from(
                workManagementService.findAll(new WorkFilter(name, reference, finalCustomerId, finalCustomerName, finalCustomerDocument,
                                status, executionLocationType,
                                startedFrom, startedTo, expectedCompletionFrom, expectedCompletionTo),
                        new PageQuery(page, size, sort).toPageable(SORT_FIELDS)),
                this::toResponse
        );
    }

    @PostMapping
    @Operation(summary = "Creates a work directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Work created", content = @Content(schema = @Schema(implementation = WorkChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Work creation submitted for approval", content = @Content(schema = @Schema(implementation = WorkChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<WorkChangeResponse> create(@Valid @RequestBody CreateWorkRequest request) {
        var result = workManagementService.create(new CreateWorkCommand(
                request.finalCustomerId(), request.name(), request.reference(), request.executionLocationType(),
                request.executionAddress(), request.status(), request.startedOn(), request.expectedCompletionOn(),
                request.completedOn(), request.justification()
        ));
        WorkResponse work = result.work() == null ? null : toResponse(result.work());
        return ResponseEntity.status(result.work() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new WorkChangeResponse(
                        work,
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    @GetMapping("/{workId}")
    @Operation(summary = "Finds a work in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Work found", content = @Content(schema = @Schema(implementation = WorkResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public WorkResponse find(@PathVariable UUID workId) {
        return toResponse(workManagementService.find(workId));
    }

    @PutMapping("/{workId}")
    @Operation(summary = "Updates a work while preserving its final customer relationship")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Work updated", content = @Content(schema = @Schema(implementation = WorkChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Work update submitted for approval", content = @Content(schema = @Schema(implementation = WorkChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<WorkChangeResponse> update(@PathVariable UUID workId,
                                                      @Valid @RequestBody UpdateWorkRequest request) {
        var result = workManagementService.update(workId, new UpdateWorkCommand(request.name(), request.reference(),
                request.executionLocationType(), request.executionAddress(), request.status(), request.startedOn(),
                request.expectedCompletionOn(), request.completedOn(), request.justification()));
        WorkResponse work = result.work() == null ? null : toResponse(result.work());
        return ResponseEntity.status(work == null ? HttpStatus.ACCEPTED : HttpStatus.OK).body(new WorkChangeResponse(work,
                result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    private WorkResponse toResponse(Work work) {
        return new WorkResponse(
                work.getId(), work.getFinalCustomerId(), work.getName(), work.getReference(),
                work.getExecutionLocationType(), work.getExecutionAddress(), work.getStatus(), work.getStartedOn(),
                work.getExpectedCompletionOn(), work.getCompletedOn()
        );
    }
}
