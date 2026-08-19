package com.renovar.canteiro.io.platform.company.api;

import com.renovar.canteiro.io.platform.company.application.CompanyOnboardingCommand;
import com.renovar.canteiro.io.platform.company.application.CompanyOnboardingResult;
import com.renovar.canteiro.io.platform.company.application.CompanyOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/companies")
@SecurityRequirements
@RequiredArgsConstructor
@Tag(name = "Company onboarding")
public class CompanyOnboardingController {

    private final CompanyOnboardingService companyOnboardingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a company after an obligatory plan selection")
    public CompanyOnboardingResponse onboard(@Valid @RequestBody CompanyOnboardingCreateRequest request) {
        CompanyOnboardingResult result = companyOnboardingService.onboard(new CompanyOnboardingCommand(
                request.corporateName(), request.tradeName(), request.document(), request.email(), request.phone(),
                request.address(), request.logo(), request.ownerEmail(), request.planIds()
        ));
        return new CompanyOnboardingResponse(
                result.companyId(), result.ownerUserId(), result.ownerEmail(), result.selectedPlanIds(),
                result.priceQuote().amount(), result.priceQuote().effectiveDate(), result.priceQuote().source(),
                result.priceQuote().planBundleId()
        );
    }
}
