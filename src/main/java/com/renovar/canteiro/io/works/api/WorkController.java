package com.renovar.canteiro.io.works.api;

import com.renovar.canteiro.io.works.api.request.CreateWorkRequest;
import com.renovar.canteiro.io.works.api.response.WorkChangeResponse;
import com.renovar.canteiro.io.works.api.response.WorkResponse;
import com.renovar.canteiro.io.works.application.CreateWorkCommand;
import com.renovar.canteiro.io.works.application.WorkManagementService;
import com.renovar.canteiro.io.works.domain.Work;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private static final Set<String> SORT_FIELDS = Set.of("name", "reference", "status", "createdAt", "updatedAt");

    @GetMapping
    @Operation(summary = "Lists works in the authenticated company")
    public PageResponse<WorkResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(
                workManagementService.findAll(new PageQuery(page, size, sort).toPageable(SORT_FIELDS)),
                this::toResponse
        );
    }

    @PostMapping
    @Operation(summary = "Creates a work directly or submits it for approval")
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
    public WorkResponse find(@PathVariable UUID workId) {
        return toResponse(workManagementService.find(workId));
    }

    private WorkResponse toResponse(Work work) {
        return new WorkResponse(
                work.getId(), work.getFinalCustomerId(), work.getName(), work.getReference(),
                work.getExecutionLocationType(), work.getExecutionAddress(), work.getStatus(), work.getStartedOn(),
                work.getExpectedCompletionOn(), work.getCompletedOn()
        );
    }
}
