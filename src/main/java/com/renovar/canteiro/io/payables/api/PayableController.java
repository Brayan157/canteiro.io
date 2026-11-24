package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.application.PayableService;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import com.renovar.canteiro.io.payables.domain.PayableFilter;
import com.renovar.canteiro.io.payables.domain.PayableView;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping(value="/api/v1/company/payables",produces="application/json")
@Tag(name="Payables")
@ApiResponses({@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="401",ref="#/components/responses/ProblemDetail"),
        @ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
public class PayableController {
    private final PayableService service;
    private final PayableApiMapper mapper;
    @PostMapping @Operation(summary="Creates individual payable titles in one financial group",description="Requires PAYABLES.CREATE authority: direct or REQUEST_CREATE+APPROVE; request-only returns 202 without official effects. operationId is tenant-scoped and idempotent for normalized identical payloads; different data returns 409. All titles share scope, links and immutable currency; each explicit amount/due date is preserved, without automatic division. Active supplier/category are optional. Approval revalidates their activation and tenant/contract chain atomically. No payments or external money transfer.")
    @ApiResponses({@ApiResponse(responseCode="201",useReturnTypeSchema=true),@ApiResponse(responseCode="200",description="Idempotent replay of official titles",useReturnTypeSchema=true),@ApiResponse(responseCode="202",description="Pending approval only; replay reuses the pending request",useReturnTypeSchema=true)})
    public ResponseEntity<PayableCreationResponse> create(@Valid @RequestBody PayableCreateRequest request){
        var result=service.create(request.toCommand());
        var response=new PayableCreationResponse(result.financialGroupId(),result.financialGroupId()==null?null:
                service.createdGroupPositions(result.financialGroupId()).stream().map(mapper::from).toList(),
                result.changeRequest()==null?null:result.changeRequest().getId(),result.mode());
        return ResponseEntity.status(result.changeRequest()!=null?202:result.replay()?200:201).body(response);
    }
    @GetMapping @Operation(summary="Lists official tenant payables",description="Requires PAYABLES.READ. Includes manual and tax titles, including cancelled history; pending requests never appear. Exact optional filters: financialScope, workId, contractId, supplierId, financialCategoryId, financialGroupId, currency, status. reference is a case-insensitive literal fragment; dueFrom/dueTo are inclusive. page >=0 (default 0); size 1..100 (default 20). Sort whitelist: dueOn, issuedOn, reference, originalAmount, createdAt, updatedAt; default dueOn asc, ties id asc. No companyId input, unscoped totals or implicit currency conversion. GET never changes status.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public PageResponse<PayableResponse> list(@RequestParam(required=false) PayableFinancialScope financialScope,
            @RequestParam(required=false) UUID workId,@RequestParam(required=false) UUID contractId,@RequestParam(required=false) UUID supplierId,
            @RequestParam(required=false) UUID financialCategoryId,@RequestParam(required=false) UUID financialGroupId,@RequestParam(required=false) CurrencyCode currency,
            @RequestParam(required=false) PayableStatus status,@RequestParam(required=false) String reference,@RequestParam(required=false) LocalDate dueFrom,
            @RequestParam(required=false) LocalDate dueTo,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,
            @Parameter(description="Filters persisted statuses before pagination and combines with status and other filters. OUTSTANDING: OPEN, OVERDUE and PARTIALLY_PAID, including titles with unavailable balances. HISTORY: PAID, CANCELLED, RENEGOTIATED and DRAFT. Omit to include all statuses.")
            @RequestParam(required=false) PayableView view,
            @Parameter(description="Allowed: dueOn,issuedOn,reference,originalAmount,createdAt,updatedAt followed by ,asc or ,desc; repeat for multiple fields.") @RequestParam(required=false) List<String> sort){
        return PageResponse.from(service.list(new PayableFilter(financialScope,workId,contractId,supplierId,financialCategoryId,financialGroupId,currency,status,reference,dueFrom,dueTo,view),
                new PageQuery(page,size,sort).toPageable(Set.of("dueOn","issuedOn","reference","originalAmount","createdAt","updatedAt"))),mapper::from);
    }
    @GetMapping("/{payableId}") @Operation(summary="Reads an official tenant payable",description="Requires PAYABLES.READ. Unknown or foreign-tenant title returns 404 without revealing existence; safe work/contract/supplier/category labels need no extra UUID lookup. No bank, document or contact data is returned.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public PayableResponse detail(@PathVariable UUID payableId){return mapper.from(service.detail(payableId));}
}
