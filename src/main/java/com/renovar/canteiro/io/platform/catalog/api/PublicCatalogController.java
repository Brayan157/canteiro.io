package com.renovar.canteiro.io.platform.catalog.api;

import com.renovar.canteiro.io.platform.catalog.api.response.PublicCatalogResponse;
import com.renovar.canteiro.io.platform.catalog.application.PublicCatalogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
@SecurityRequirements
@Tag(name = "Public catalog")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final PublicCatalogQueryService publicCatalogQueryService;

    @GetMapping
    @Operation(summary = "Lists active plans, bundles and prices effective today for public onboarding")
    public PublicCatalogResponse currentCatalog() {
        return publicCatalogQueryService.currentCatalog();
    }
}
