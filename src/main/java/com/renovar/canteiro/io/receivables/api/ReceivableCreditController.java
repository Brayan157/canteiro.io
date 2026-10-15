package com.renovar.canteiro.io.receivables.api;
import com.renovar.canteiro.io.receivables.api.request.CancelFinancialRecordRequest;
import com.renovar.canteiro.io.receivables.api.request.CreateReceivableCreditRequest;
import com.renovar.canteiro.io.receivables.api.response.ReceivableCreditResponse;
import com.renovar.canteiro.io.receivables.application.ReceivableCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;
@RestController @RequestMapping("/api/v1/company/receivables") @RequiredArgsConstructor @Tag(name="Receivables")
@ApiResponses({@ApiResponse(responseCode="201",description="Official credit/reversal recorded",useReturnTypeSchema=true),
 @ApiResponse(responseCode="202",description="Approval proposal only",useReturnTypeSchema=true),@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),
 @ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),
 @ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
public class ReceivableCreditController{
 private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;
 private final ReceivableCreditService credits;
 @PostMapping("/{receivableId}/credits") @Operation(summary="Applies a justified receivable credit without inventing a cash receipt or freeing billing capacity")
 public ResponseEntity<ReceivableCreditResponse> create(@PathVariable UUID receivableId,@Valid @RequestBody CreateReceivableCreditRequest r){
    var response=credits.create(receivableId,new BigDecimal(r.amount()),r.reason(),r.justification());
    return ResponseEntity.status(response.changeRequestId()==null?201:202).body(response.withCurrency(currencies.forReceivable(receivableId)));
 }
 @PostMapping("/{receivableId}/credits/{creditId}/reversal") @Operation(summary="Records an immutable reversal of a receivable credit")
 public ResponseEntity<ReceivableCreditResponse> reverse(@PathVariable UUID receivableId,@PathVariable UUID creditId,@Valid @RequestBody CancelFinancialRecordRequest r){
    var response=credits.reverse(receivableId,creditId,r.reason(),r.justification());
    return ResponseEntity.status(response.changeRequestId()==null?201:202).body(response.withCurrency(currencies.forReceivable(receivableId)));
 }
}

