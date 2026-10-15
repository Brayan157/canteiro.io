package com.renovar.canteiro.io.shared.api;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CurrencyController {
    private final TenantContextHolder tenants;

    @GetMapping("/api/v1/company/currencies")
    @Operation(summary = "Lists supported monetary units in the authenticated company context",
            description = "BRL is the default. Currency is immutable from creation, including drafts. Linked measurements inherit the contract currency if omitted. No exchange rates or conversion. Monetary JSON values remain canonical decimal strings with two places; symbols are presentation only. Consolidations must remain separated by currency.")
    @ApiResponses({@ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")})
    public List<CurrencyResponse> currencies() {
        tenants.requireCurrentTenant();
        return Arrays.stream(CurrencyCode.values()).map(c -> new CurrencyResponse(c, c.getSymbol(),
                c.getDisplayName(), c.getDecimalPlaces(), c == CurrencyCode.BRL)).toList();
    }

    public record CurrencyResponse(CurrencyCode code, String symbol, String displayName, int decimalPlaces,
                                   boolean defaultCurrency) { }
}
