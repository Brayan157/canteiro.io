package com.renovar.canteiro.io.employees.api;

import com.renovar.canteiro.io.employees.api.request.CreateEmployeeRequest;
import com.renovar.canteiro.io.employees.api.request.InviteEmployeeAccessRequest;
import com.renovar.canteiro.io.employees.api.response.EmployeeAccessInvitationResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeeChangeResponse;
import com.renovar.canteiro.io.employees.api.response.EmployeeResponse;
import com.renovar.canteiro.io.employees.application.CreateEmployeeCommand;
import com.renovar.canteiro.io.employees.application.EmployeeAccessInvitationService;
import com.renovar.canteiro.io.employees.application.EmployeeManagementService;
import com.renovar.canteiro.io.employees.application.InviteEmployeeAccessCommand;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/employees")
@Tag(name = "Employees")
@RequiredArgsConstructor
public class EmployeeController {

    private static final Set<String> SORT_FIELDS = Set.of("fullName", "createdAt", "updatedAt");

    private final EmployeeManagementService employeeManagementService;
    private final EmployeeAccessInvitationService employeeAccessInvitationService;

    @PostMapping
    @Operation(summary = "Registers an operational employee without creating system access")
    public ResponseEntity<EmployeeChangeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        var result = employeeManagementService.create(new CreateEmployeeCommand(
                request.fullName(), request.jobTitle(), request.phone(), request.justification()
        ));
        EmployeeResponse employee = result.employee() == null ? null : response(result.employee());
        return ResponseEntity.status(employee == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new EmployeeChangeResponse(employee,
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Finds an employee in the authenticated company")
    public EmployeeResponse find(@PathVariable UUID employeeId) {
        return response(employeeManagementService.find(employeeId));
    }

    @GetMapping
    @Operation(summary = "Lists employees in the authenticated company")
    public PageResponse<EmployeeResponse> findAll(@RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) List<String> sort) {
        return PageResponse.from(employeeManagementService.findAll(
                new PageQuery(page, size, sort).toPageable(SORT_FIELDS)), this::response);
    }

    @PostMapping("/{employeeId}/access-invitations")
    @Operation(summary = "Creates system access for an employee and sends an activation email")
    public ResponseEntity<EmployeeAccessInvitationResponse> inviteAccess(@PathVariable UUID employeeId,
                                                                           @Valid @RequestBody InviteEmployeeAccessRequest request) {
        var result = employeeAccessInvitationService.invite(new InviteEmployeeAccessCommand(employeeId, request.email()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new EmployeeAccessInvitationResponse(
                result.employeeId(), result.userId(), result.email()
        ));
    }

    private EmployeeResponse response(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getJobTitle(), employee.getPhone(),
                employee.isActive(), employee.getUserId());
    }
}
