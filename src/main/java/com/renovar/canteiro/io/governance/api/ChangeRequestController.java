package com.renovar.canteiro.io.governance.api;

import com.renovar.canteiro.io.governance.api.request.ChangeRequestDecisionRequest;
import com.renovar.canteiro.io.governance.api.response.ChangeRequestDecisionResponse;
import com.renovar.canteiro.io.governance.api.response.ChangeRequestEntityResponse;
import com.renovar.canteiro.io.governance.api.response.ChangeRequestRequesterResponse;
import com.renovar.canteiro.io.governance.api.response.ChangeRequestResponse;
import com.renovar.canteiro.io.governance.api.response.AppliedEntityResponse;
import com.renovar.canteiro.io.governance.application.ApproveChangeRequestCommand;
import com.renovar.canteiro.io.governance.application.ChangeRequestFilter;
import com.renovar.canteiro.io.governance.application.ChangeRequestListItem;
import com.renovar.canteiro.io.governance.application.ChangeRequestDecisionService;
import com.renovar.canteiro.io.governance.application.ChangeRequestQueryService;
import com.renovar.canteiro.io.governance.application.RejectChangeRequestCommand;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/change-requests")
@RequiredArgsConstructor
public class ChangeRequestController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "module", "operation");

    private final ChangeRequestDecisionService changeRequestDecisionService;
    private final ChangeRequestQueryService changeRequestQueryService;

    @GetMapping
    @Operation(summary = "Lists pending tenant-scoped change requests with safe search and approval projections")
    public PageResponse<ChangeRequestResponse> findPending(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) com.renovar.canteiro.io.governance.domain.AuditModule module,
            @RequestParam(required = false) com.renovar.canteiro.io.governance.domain.ChangeRequestOperation operation,
            @RequestParam(required = false) UUID requesterUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo
    ) {
        return PageResponse.from(
                changeRequestQueryService.findPending(
                        new ChangeRequestFilter(search, module, operation, requesterUserId, createdFrom, createdTo),
                        new PageQuery(page, size, sort).toPageable(SORT_FIELDS)
                ),
                this::toResponse
        );
    }

    @PostMapping("/{id}/approve")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Change request approved", content = @Content(schema = @Schema(implementation = ChangeRequestDecisionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ChangeRequestDecisionResponse approve(
            @PathVariable UUID id,
            @RequestBody ChangeRequestDecisionRequest request
    ) {
        var result = changeRequestDecisionService.approveWithAppliedEntity(
                new ApproveChangeRequestCommand(id, request.decisionReason())
        );
        return new ChangeRequestDecisionResponse(
                result.changeRequest().getId(), result.changeRequest().getStatus(), toAppliedEntityResponse(result.appliedEntity())
        );
    }

    @PostMapping("/{id}/reject")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Change request rejected", content = @Content(schema = @Schema(implementation = ChangeRequestDecisionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ChangeRequestDecisionResponse reject(
            @PathVariable UUID id,
            @RequestBody ChangeRequestDecisionRequest request
    ) {
        var result = changeRequestDecisionService.reject(new RejectChangeRequestCommand(id, request.decisionReason()));
        return new ChangeRequestDecisionResponse(result.getId(), result.getStatus(), null);
    }

    private ChangeRequestResponse toResponse(ChangeRequestListItem item) {
        var request = item.changeRequest();
        return new ChangeRequestResponse(
                request.getId(), request.getRequesterUserId(),
                new ChangeRequestRequesterResponse(item.requester().id(), item.requester().displayName(),
                        item.requester().employeeId(), item.requester().photoUrl()),
                request.getModule(), request.getOperation(), request.getEntityType(), request.getEntityId(),
                new ChangeRequestEntityResponse(item.entity().type(), item.entity().id(), item.entity().displayName()),
                request.getEntityVersion(), request.getRevision(), request.getStatus(), item.previousData(), item.proposedData(),
                item.proposedSummary(), request.getJustification(), request.getCreatedAt()
        );
    }

    private AppliedEntityResponse toAppliedEntityResponse(
            com.renovar.canteiro.io.governance.application.AppliedChangeRequestEntity appliedEntity
    ) {
        return appliedEntity == null ? null : new AppliedEntityResponse(
                appliedEntity.type(), appliedEntity.id(), appliedEntity.displayName()
        );
    }
}
