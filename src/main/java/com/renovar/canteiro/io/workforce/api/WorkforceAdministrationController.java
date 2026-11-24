package com.renovar.canteiro.io.workforce.api;

import com.renovar.canteiro.io.workforce.application.WorkforceAdministrationService;
import com.renovar.canteiro.io.workforce.domain.EmployeeEmployment;
import com.renovar.canteiro.io.workforce.domain.WorkAccessGrant;
import com.renovar.canteiro.io.workforce.domain.WorkAssignment;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/workforce")
public class WorkforceAdministrationController {
    private final WorkforceAdministrationService administration;

    public WorkforceAdministrationController(WorkforceAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/employments")
    public List<EmploymentResponse> listWorkforceEmployments(@RequestParam UUID employeeId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return administration.employments(employeeId, offset, limit).stream().map(EmploymentResponse::from).toList();
    }

    @GetMapping("/assignments")
    public List<AssignmentSelectionResponse> listWorkforceAssignments(@RequestParam UUID workId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return administration.assignments(workId, from, through, offset, limit).stream()
                .map(row -> new AssignmentSelectionResponse(AssignmentResponse.from(row.assignment()), row.employeeName())).toList();
    }

    @GetMapping("/grants")
    public List<GrantSelectionResponse> listWorkforceGrants(@RequestParam UUID workId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return administration.grants(workId, userId, offset, limit).stream()
                .map(row -> new GrantSelectionResponse(GrantResponse.from(row.grant()), row.userLabel())).toList();
    }

    @PostMapping("/employments")
    @ResponseStatus(HttpStatus.CREATED)
    public EmploymentResponse recordEmployment(@Valid @RequestBody EmploymentCreateRequest request) {
        return EmploymentResponse.from(administration.recordEmployment(request.employeeId(), request.startedOn(), request.endedOn()));
    }

    @PutMapping("/employments/{employmentId}/end")
    public EmploymentResponse endEmployment(@PathVariable UUID employmentId, @Valid @RequestBody EndPeriodUpdateRequest request) {
        return EmploymentResponse.from(administration.endEmployment(employmentId, request.endedOn(), request.expectedVersion()));
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse recordAssignment(@Valid @RequestBody AssignmentCreateRequest request) {
        return AssignmentResponse.from(administration.recordAssignment(request.workId(), request.employmentId(),
                request.startedOn(), request.endedOn()));
    }

    @PostMapping("/assignments/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AssignmentResponse> recordAssignmentBatch(@Valid @RequestBody AssignmentBatchCreateRequest request) {
        return administration.recordAssignments(request.workId(), request.employeeIds(), request.startedOn()).stream()
                .map(AssignmentResponse::from).toList();
    }

    @PutMapping("/assignments/{assignmentId}/end")
    public AssignmentResponse endAssignment(@PathVariable UUID assignmentId, @Valid @RequestBody EndPeriodUpdateRequest request) {
        return AssignmentResponse.from(administration.endAssignment(assignmentId, request.endedOn(), request.expectedVersion()));
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse grantAccess(@Valid @RequestBody GrantCreateRequest request) {
        return GrantResponse.from(administration.grantAccess(request.workId(), request.userId(), request.operation(),
                request.startsAt(), request.endsAt()));
    }

    @PutMapping("/grants/{grantId}/revoke")
    public GrantResponse revokeAccess(@PathVariable UUID grantId) {
        return GrantResponse.from(administration.revokeAccess(grantId));
    }

    public record EmploymentCreateRequest(@NotNull UUID employeeId, @NotNull LocalDate startedOn, LocalDate endedOn) {
    }

    public record AssignmentCreateRequest(@NotNull UUID workId, @NotNull UUID employmentId,
                                          @NotNull LocalDate startedOn, LocalDate endedOn) {
    }

    public record AssignmentBatchCreateRequest(@NotNull UUID workId,
                                               @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> employeeIds,
                                               @NotNull LocalDate startedOn) {
    }

    public record EndPeriodUpdateRequest(@NotNull LocalDate endedOn, @NotNull @PositiveOrZero Long expectedVersion) {
    }

    public record AssignmentSelectionResponse(AssignmentResponse assignment, String employeeName) { }

    public record GrantSelectionResponse(GrantResponse grant, String userLabel) { }

    public record GrantCreateRequest(@NotNull UUID workId, @NotNull UUID userId, @NotNull WorkOperation operation,
                                     @NotNull Instant startsAt, Instant endsAt) {
    }

    public record EmploymentResponse(UUID id, UUID employeeId, LocalDate startedOn, LocalDate endedOn, long version) {
        static EmploymentResponse from(EmployeeEmployment employment) {
            return new EmploymentResponse(employment.id(), employment.employeeId(), employment.startedOn(),
                    employment.endedOn(), employment.version());
        }
    }

    public record AssignmentResponse(UUID id, UUID workId, UUID employeeId, UUID employmentId,
                                     LocalDate startedOn, LocalDate endedOn, long version) {
        static AssignmentResponse from(WorkAssignment assignment) {
            return new AssignmentResponse(assignment.id(), assignment.workId(), assignment.employeeId(),
                    assignment.employmentId(), assignment.startedOn(), assignment.endedOn(), assignment.version());
        }
    }

    public record GrantResponse(UUID id, UUID workId, UUID userId, WorkOperation operation, Instant startsAt,
                                Instant endsAt, Instant revokedAt) {
        static GrantResponse from(WorkAccessGrant grant) {
            return new GrantResponse(grant.id(), grant.workId(), grant.userId(), grant.operation(), grant.startsAt(),
                    grant.endsAt(), grant.revokedAt());
        }
    }
}
