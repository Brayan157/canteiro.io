package com.renovar.canteiro.io.contracts.api;

import com.renovar.canteiro.io.contracts.api.request.CreateContractRequest;
import com.renovar.canteiro.io.contracts.api.response.ContractChangeResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractResponse;
import com.renovar.canteiro.io.contracts.application.ContractManagementService;
import com.renovar.canteiro.io.contracts.application.CreateContractCommand;
import com.renovar.canteiro.io.contracts.domain.Contract;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts")
public class ContractController {

    private final ContractManagementService contractManagementService;

    @PostMapping
    @Operation(summary = "Creates a contract without requiring services")
    public ResponseEntity<ContractChangeResponse> create(@Valid @RequestBody CreateContractRequest request) {
        var result = contractManagementService.create(new CreateContractCommand(
                request.workId(), request.reference(), request.name(), request.status(), request.startedOn(),
                request.expectedCompletionOn(), request.completedOn(), request.justification()
        ));
        ContractResponse contract = result.contract() == null ? null : toResponse(result.contract());
        return ResponseEntity.status(result.contract() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new ContractChangeResponse(
                        contract,
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "Finds a contract in the authenticated company")
    public ContractResponse find(@PathVariable UUID contractId) {
        return toResponse(contractManagementService.find(contractId));
    }

    private ContractResponse toResponse(Contract contract) {
        return new ContractResponse(
                contract.getId(), contract.getWorkId(), contract.getReference(), contract.getName(), contract.getStatus(),
                contract.getStartedOn(), contract.getExpectedCompletionOn(), contract.getCompletedOn()
        );
    }
}
