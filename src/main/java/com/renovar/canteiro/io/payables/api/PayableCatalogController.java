package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.payables.application.PayableCatalogService;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import com.renovar.canteiro.io.payables.api.PayableCatalogRequests.SupplierCreateRequest;
import com.renovar.canteiro.io.payables.api.PayableCatalogRequests.SupplierUpdateRequest;
import com.renovar.canteiro.io.payables.api.PayableCatalogRequests.FinancialCategoryCreateRequest;
import com.renovar.canteiro.io.payables.api.PayableCatalogRequests.FinancialCategoryUpdateRequest;
import com.renovar.canteiro.io.payables.api.PayableCatalogResponses.SupplierResponse;
import com.renovar.canteiro.io.payables.api.PayableCatalogResponses.SupplierChangeResponse;
import com.renovar.canteiro.io.payables.api.PayableCatalogResponses.FinancialCategoryResponse;
import com.renovar.canteiro.io.payables.api.PayableCatalogResponses.FinancialCategoryChangeResponse;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequiredArgsConstructor @Tag(name="Payable catalogs")
@org.springframework.web.bind.annotation.RequestMapping(produces="application/json")
@ApiResponses({@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),
        @ApiResponse(responseCode="401",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
        @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
public class PayableCatalogController {
    private final PayableCatalogService catalog;
    @GetMapping("/api/v1/company/suppliers")
    @Operation(summary="Lists tenant suppliers",description="Requires PAYABLES.READ. Name is a case-insensitive literal fragment; document is an exact normalized alphanumeric identifier. Active defaults true. Pagination: page >= 0; size 1..100 (default 20). Sort only name,createdAt,updatedAt; default name asc, ties id asc. No free tenant parameter.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public PageResponse<SupplierResponse> suppliers(@RequestParam(required=false) String name,@RequestParam(required=false) String document,
            @RequestParam(defaultValue="true") Boolean active,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,
            @Parameter(description="name,asc|desc; createdAt,asc|desc; updatedAt,asc|desc; repeat for multiple fields") @RequestParam(required=false) List<String> sort){
        return PageResponse.from(catalog.suppliers(name,document,active,new PageQuery(page,size,sort).toPageable(Set.of("name","createdAt","updatedAt"))),SupplierResponse::from);
    }
    @GetMapping("/api/v1/company/suppliers/{id}") @Operation(summary="Reads a tenant supplier, including inactive history",description="Requires PAYABLES.READ; foreign or unknown IDs return 404.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public SupplierResponse supplier(@PathVariable UUID id){return SupplierResponse.from(catalog.supplier(id));}
    @PostMapping("/api/v1/company/suppliers") @Operation(summary="Creates supplier with PAYABLES creation authority")
    @ApiResponses({@ApiResponse(responseCode="201",useReturnTypeSchema=true),@ApiResponse(responseCode="202",useReturnTypeSchema=true)})
    public ResponseEntity<SupplierChangeResponse> createSupplier(@Valid @RequestBody SupplierCreateRequest r){
        var result=catalog.changeSupplier(null,ChangeOperation.CREATE,0,r.name(),r.document(),r.contactName(),r.contactPhone(),r.contactEmail(),r.justification());
        return ResponseEntity.status(result.changeRequest()==null?201:202).body(SupplierChangeResponse.from(result));
    }
    @PutMapping("/api/v1/company/suppliers/{id}") @Operation(summary="Updates supplier with PAYABLES update authority and expectedVersion")
    @ApiResponses({@ApiResponse(responseCode="200",useReturnTypeSchema=true),@ApiResponse(responseCode="202",useReturnTypeSchema=true)})
    public ResponseEntity<SupplierChangeResponse> updateSupplier(@PathVariable UUID id,@Valid @RequestBody SupplierUpdateRequest r){
        var result=catalog.changeSupplier(id,ChangeOperation.UPDATE,r.expectedVersion(),r.name(),r.document(),r.contactName(),r.contactPhone(),r.contactEmail(),r.justification());
        return ResponseEntity.status(result.changeRequest()==null?200:202).body(SupplierChangeResponse.from(result));
    }
    @DeleteMapping("/api/v1/company/suppliers/{id}") @Operation(summary="Logically deactivates supplier with PAYABLES cancel authority",description="Never physically deletes. expectedVersion is mandatory; stale record/proposal returns 409. 202 does not deactivate anything until approval.")
    @ApiResponses({@ApiResponse(responseCode="204",description="Deactivated",content=@Content),@ApiResponse(responseCode="202",content=@Content(schema=@Schema(implementation=SupplierChangeResponse.class)))})
    public ResponseEntity<SupplierChangeResponse> deleteSupplier(@PathVariable UUID id,@RequestParam long expectedVersion,@RequestParam(required=false) String justification){
        var result=catalog.changeSupplier(id,ChangeOperation.CANCEL,expectedVersion,null,null,justification);
        return result.changeRequest()==null?ResponseEntity.noContent().build():ResponseEntity.accepted().body(SupplierChangeResponse.from(result));
    }
    @GetMapping("/api/v1/company/financial-categories")
    @Operation(summary="Lists tenant expense classifications",description="Requires COSTS_EXPENSES.READ. Name is a literal case-insensitive fragment; optional type filter. Active defaults true; page >= 0; size 1..100 (default 20). Allowed sorts: name,createdAt,updatedAt; default name asc, ties id asc. Classification never selects WORK/COMPANY scope automatically.")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public PageResponse<FinancialCategoryResponse> categories(@RequestParam(required=false) String name,@RequestParam(required=false) FinancialCategoryType type,
            @RequestParam(defaultValue="true") Boolean active,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@RequestParam(required=false) List<String> sort){
        return PageResponse.from(catalog.categories(name,type,active,new PageQuery(page,size,sort).toPageable(Set.of("name","createdAt","updatedAt"))),FinancialCategoryResponse::from);
    }
    @GetMapping("/api/v1/company/financial-categories/{id}") @Operation(summary="Reads category with COSTS_EXPENSES.READ, including inactive history")
    @ApiResponse(responseCode="200",useReturnTypeSchema=true)
    public FinancialCategoryResponse category(@PathVariable UUID id){return FinancialCategoryResponse.from(catalog.category(id));}
    @PostMapping("/api/v1/company/financial-categories") @Operation(summary="Creates category with COSTS_EXPENSES creation authority",description="Code is uppercased and unique within the tenant, including inactive history; code/type are immutable. A category does not create a payable or determine its financial scope.")
    @ApiResponses({@ApiResponse(responseCode="201",useReturnTypeSchema=true),@ApiResponse(responseCode="202",useReturnTypeSchema=true)})
    public ResponseEntity<FinancialCategoryChangeResponse> createCategory(@Valid @RequestBody FinancialCategoryCreateRequest r){
        var result=catalog.changeCategory(null,ChangeOperation.CREATE,0,r.code(),r.name(),r.description(),r.type(),r.justification());
        return ResponseEntity.status(result.changeRequest()==null?201:202).body(FinancialCategoryChangeResponse.from(result));
    }
    @PutMapping("/api/v1/company/financial-categories/{id}") @Operation(summary="Updates category label/description with COSTS_EXPENSES update authority",description="Requires expectedVersion. Code and classification cannot change or reinterpret historical expenses.")
    @ApiResponses({@ApiResponse(responseCode="200",useReturnTypeSchema=true),@ApiResponse(responseCode="202",useReturnTypeSchema=true)})
    public ResponseEntity<FinancialCategoryChangeResponse> updateCategory(@PathVariable UUID id,@Valid @RequestBody FinancialCategoryUpdateRequest r){
        var result=catalog.changeCategory(id,ChangeOperation.UPDATE,r.expectedVersion(),null,r.name(),r.description(),null,r.justification());
        return ResponseEntity.status(result.changeRequest()==null?200:202).body(FinancialCategoryChangeResponse.from(result));
    }
    @DeleteMapping("/api/v1/company/financial-categories/{id}") @Operation(summary="Logically deactivates category with COSTS_EXPENSES cancel authority",description="expectedVersion is mandatory. 202 leaves official data unchanged; no physical deletion.")
    @ApiResponses({@ApiResponse(responseCode="204",description="Deactivated",content=@Content),@ApiResponse(responseCode="202",content=@Content(schema=@Schema(implementation=FinancialCategoryChangeResponse.class)))})
    public ResponseEntity<FinancialCategoryChangeResponse> deleteCategory(@PathVariable UUID id,@RequestParam long expectedVersion,@RequestParam(required=false) String justification){
        var result=catalog.changeCategory(id,ChangeOperation.CANCEL,expectedVersion,null,null,null,null,justification);
        return result.changeRequest()==null?ResponseEntity.noContent().build():ResponseEntity.accepted().body(FinancialCategoryChangeResponse.from(result));
    }
}
