package com.renovar.canteiro.io.contracts.api;

import com.renovar.canteiro.io.contracts.api.response.ServiceTemplateResponse;
import com.renovar.canteiro.io.contracts.application.ServiceTemplateQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/company/service-templates")
@RequiredArgsConstructor
@Tag(name = "Service templates")
public class ServiceTemplateController {
    private final ServiceTemplateQueryService serviceTemplateQueryService;

    @GetMapping
    @Operation(summary = "Lists service templates available to the authenticated company")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Service templates"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")})
    public List<ServiceTemplateResponse> findAll(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return serviceTemplateQueryService.findAll(includeInactive).stream()
                .map(template -> new ServiceTemplateResponse(template.getId(), template.getName(), template.getDescription(), template.isActive()))
                .toList();
    }
}
