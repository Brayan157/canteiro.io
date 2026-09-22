package com.renovar.canteiro.io.platform.company.api;

import com.renovar.canteiro.io.platform.company.api.response.PlatformCompanyDetailsResponse;
import com.renovar.canteiro.io.platform.company.api.response.PlatformCompanySummaryResponse;
import com.renovar.canteiro.io.platform.company.application.PlatformCompanyQueryService;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/companies")
@Tag(name = "Platform companies")
@RequiredArgsConstructor
public class PlatformCompanyController {

    private static final Set<String> COMPANY_SORT_FIELDS = Set.of(
            "corporateName", "tradeName", "createdAt", "updatedAt"
    );

    private final PlatformCompanyQueryService platformCompanyQueryService;

    @GetMapping
    @Operation(summary = "Lists companies and current subscription status for the platform owner")
    public PageResponse<PlatformCompanySummaryResponse> findCompanies(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(
                platformCompanyQueryService.findCompanies(
                        new PageQuery(page, size, sort).toPageable(COMPANY_SORT_FIELDS)
                ),
                company -> company
        );
    }

    @GetMapping("/{companyId}")
    @Operation(summary = "Returns company and subscription details for the platform owner")
    public PlatformCompanyDetailsResponse findCompany(@PathVariable UUID companyId) {
        return platformCompanyQueryService.findCompany(companyId);
    }
}
