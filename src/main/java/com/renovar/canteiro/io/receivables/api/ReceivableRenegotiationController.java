package com.renovar.canteiro.io.receivables.api;
import com.renovar.canteiro.io.receivables.api.request.CancelFinancialRecordRequest;
import com.renovar.canteiro.io.receivables.api.request.RenegotiateReceivableRequest;
import com.renovar.canteiro.io.receivables.api.response.ReceivableRenegotiationResponse;
import com.renovar.canteiro.io.receivables.application.ReceivableRenegotiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/company/receivables") @RequiredArgsConstructor @Tag(name="Receivables")
@ApiResponses({@ApiResponse(responseCode="200",description="Official titles, with their preserved installment origin",useReturnTypeSchema=true),
 @ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
 @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
public class ReceivableRenegotiationController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;
    private final ReceivableRenegotiationService service;
    @GetMapping("/{receivableId}/renegotiations") @Operation(summary="Lists descendant titles of a renegotiation, including cancelled historical titles")
    public ReceivableRenegotiationResponse list(@PathVariable UUID receivableId){return service.list(receivableId).withCurrency(currencies.forReceivable(receivableId));}
    @PostMapping("/{receivableId}/renegotiations") @Operation(summary="Reschedules only the unpaid balance, without new billing, forgiveness or cash")
    @ApiResponse(responseCode="202",description="Approval proposal only, without official titles",useReturnTypeSchema=true)
    public ResponseEntity<ReceivableRenegotiationResponse> create(@PathVariable UUID receivableId,@Valid @RequestBody RenegotiateReceivableRequest r){
        var response=service.change(receivableId,r);return ResponseEntity.status(response.changeRequestId()==null?200:202).body(response.withCurrency(currencies.forReceivable(receivableId)));
    }
    @PostMapping("/{receivableId}/renegotiations/reversal") @Operation(summary="Cancels descendant titles logically and reopens the original unpaid balance")
    @ApiResponse(responseCode="202",description="Approval proposal only",useReturnTypeSchema=true)
    public ResponseEntity<ReceivableRenegotiationResponse> reverse(@PathVariable UUID receivableId,@Valid @RequestBody CancelFinancialRecordRequest r){
        var response=service.reverse(receivableId,r.reason(),r.justification());return ResponseEntity.status(response.changeRequestId()==null?200:202).body(response.withCurrency(currencies.forReceivable(receivableId)));
    }
}
