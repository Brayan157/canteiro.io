package com.renovar.canteiro.io.contracts.api;

import com.renovar.canteiro.io.contracts.api.request.CreateContractRequest;
import com.renovar.canteiro.io.contracts.api.request.SetContractDiscountRequest;
import com.renovar.canteiro.io.contracts.api.request.UpsertContractServiceRequest;
import com.renovar.canteiro.io.contracts.api.request.UpdateContractRequest;
import com.renovar.canteiro.io.contracts.api.response.ContractChangeResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractFinancialTotalsResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractServiceChangeResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractServiceResponse;
import com.renovar.canteiro.io.contracts.application.ContractManagementService;
import com.renovar.canteiro.io.contracts.application.ContractServiceChangeResult;
import com.renovar.canteiro.io.contracts.application.ContractServiceCommand;
import com.renovar.canteiro.io.contracts.application.ContractServiceManagementService;
import com.renovar.canteiro.io.contracts.application.CreateContractCommand;
import com.renovar.canteiro.io.contracts.application.ContractFilter;
import com.renovar.canteiro.io.contracts.application.UpdateContractCommand;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
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
import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/v1/company/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts")
public class ContractController {

    private final ContractManagementService contractManagementService;
    private final ContractServiceManagementService contractServiceManagementService;

    private static final Set<String> SORT_FIELDS = Set.of(
            "name", "reference", "status", "startedOn", "expectedCompletionOn", "completedOn", "createdAt", "updatedAt"
    );

    @GetMapping
    @Operation(summary = "Lists contracts in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated contract list", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<ContractResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) UUID workId,
            @RequestParam(required = false) String workName,
            @RequestParam(required = false) String finalCustomerName,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) java.time.LocalDate startedFrom,
            @RequestParam(required = false) java.time.LocalDate startedTo,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionFrom,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionTo,
            @RequestParam(required = false) java.time.LocalDate completedFrom,
            @RequestParam(required = false) java.time.LocalDate completedTo
    ) {
        return PageResponse.from(
                contractManagementService.findAll(new ContractFilter(name, reference, workId, workName, finalCustomerName,
                                status, startedFrom, startedTo,
                                expectedCompletionFrom, expectedCompletionTo, completedFrom, completedTo),
                        new PageQuery(page, size, sort).toPageable(SORT_FIELDS)),
                this::toResponse
        );
    }

    @PostMapping
    @Operation(summary = "Creates a contract without requiring services")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contract created", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contract creation submitted for approval", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractChangeResponse> create(@Valid @RequestBody CreateContractRequest request) {
        var result = contractManagementService.create(new CreateContractCommand(
                request.workId(), request.contractIdentifier(), request.reference(), request.name(), request.status(), request.startedOn(),
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract found", content = @Content(schema = @Schema(implementation = ContractResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ContractResponse find(@PathVariable UUID contractId) {
        return toResponse(contractManagementService.find(contractId));
    }

    @PutMapping("/{contractId}")
    @Operation(summary = "Updates a contract while preserving its work relationship")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract updated", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contract update submitted for approval", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractChangeResponse> update(@PathVariable UUID contractId,
                                                          @Valid @RequestBody UpdateContractRequest request) {
        var result = contractManagementService.update(contractId, new UpdateContractCommand(request.contractIdentifier(), request.reference(), request.name(),
                request.status(), request.startedOn(), request.expectedCompletionOn(), request.completedOn(), request.justification()));
        ContractResponse contract = result.contract() == null ? null : toResponse(result.contract());
        return ResponseEntity.status(contract == null ? HttpStatus.ACCEPTED : HttpStatus.OK).body(new ContractChangeResponse(contract,
                result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @GetMapping("/{contractId}/services")
    @Operation(summary = "Lists services belonging to a contract in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract services", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<ContractServiceResponse> findServices(@PathVariable UUID contractId) {
        return contractManagementService.findServices(contractId).stream()
                .map(this::toServiceResponse)
                .toList();
    }

    @PostMapping("/{contractId}/services")
    @Operation(summary = "Creates a service for a contract directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service created", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service creation submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> createService(@PathVariable UUID contractId,
                                                                         @Valid @RequestBody UpsertContractServiceRequest request) {
        return serviceResponse(contractServiceManagementService.create(contractId, command(request)), HttpStatus.CREATED);
    }

    @PutMapping("/{contractId}/services/{serviceId}")
    @Operation(summary = "Updates a service without changing its contract relationship")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service updated", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service update submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> updateService(@PathVariable UUID contractId, @PathVariable UUID serviceId,
                                                                         @Valid @RequestBody UpsertContractServiceRequest request) {
        return serviceResponse(contractServiceManagementService.update(contractId, serviceId, command(request)), HttpStatus.OK);
    }

    @PutMapping("/{contractId}/services/{serviceId}/discount")
    @Operation(summary = "Applies or changes the discount of one contract service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service discount updated", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service discount submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> setServiceDiscount(@PathVariable UUID contractId, @PathVariable UUID serviceId,
                                                                              @Valid @RequestBody SetContractDiscountRequest request) {
        return serviceResponse(contractServiceManagementService.setServiceDiscount(contractId, serviceId, request.discountType(),
                decimal(request.discountValue()), request.justification()), HttpStatus.OK);
    }

    @PutMapping("/{contractId}/discount")
    @Operation(summary = "Applies or changes the contract-level adjustment without altering its services")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract adjustment updated", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contract adjustment submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> setContractDiscount(@PathVariable UUID contractId,
                                                                               @Valid @RequestBody SetContractDiscountRequest request) {
        return serviceResponse(contractServiceManagementService.setContractDiscount(contractId, request.discountType(),
                decimal(request.discountValue()), request.justification()), HttpStatus.OK);
    }

    private ResponseEntity<ContractServiceChangeResponse> serviceResponse(ContractServiceChangeResult result, HttpStatus directStatus) {
        ContractServiceChangeResponse response = new ContractServiceChangeResponse(result.service() == null ? null : toServiceResponse(result.service()),
                result.contractNetAmount() == null ? null : new ContractFinancialTotalsResponse(money(result.contractNetAmount().serviceSubtotal()),
                        money(result.contractNetAmount().contractDiscountAmount()), money(result.contractNetAmount().netAmount())),
                result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode());
        return ResponseEntity.status(result.service() == null && result.contractNetAmount() == null ? HttpStatus.ACCEPTED : directStatus).body(response);
    }

    private ContractServiceCommand command(UpsertContractServiceRequest request) {
        return new ContractServiceCommand(request.sourceServiceTemplateId(), request.name(), request.description(), request.status(), decimal(request.quantity()),
                decimal(request.unitPrice()), request.discountType(), request.discountValue() == null ? null : decimal(request.discountValue()),
                request.justification());
    }

    private ContractServiceResponse toServiceResponse(com.renovar.canteiro.io.contracts.domain.ContractService service) {
        return new ContractServiceResponse(service.getId(), service.getContractId(), service.getSourceServiceTemplateId(), service.getName(),
                service.getDescription(), service.getStatus(), quantity(service.getQuantity()), money(service.getUnitPrice()), money(service.getGrossAmount()),
                service.getDiscountType(), service.getDiscountValue() == null ? null : decimalString(service.getDiscountValue(), 4),
                money(service.getDiscountAmount()), money(service.getNetAmount()));
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value); }
    private String quantity(BigDecimal value) { return decimalString(value, 4); }
    private String money(BigDecimal value) { return decimalString(value, 2); }
    private String decimalString(BigDecimal value, int scale) { return value.setScale(scale, RoundingMode.HALF_UP).toPlainString(); }

    private ContractResponse toResponse(Contract contract) {
        return new ContractResponse(
                contract.getId(), contract.getWorkId(), contract.getContractIdentifier(), contract.getReference(), contract.getName(), contract.getStatus(),
                contract.getStartedOn(), contract.getExpectedCompletionOn(), contract.getCompletedOn()
        );
    }
}
