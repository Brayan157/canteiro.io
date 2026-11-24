package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PayableCatalogRequests {
    private PayableCatalogRequests() { }
    @Schema(name="SupplierCreateRequest")
    public record SupplierCreateRequest(@NotBlank @Size(max=255) String name,@Size(max=80) String document,@Schema(types={"string","null"}) @Size(max=120) String contactName,@Schema(types={"string","null"}) @Size(max=40) String contactPhone,
            @Schema(types={"string","null"}) @Email @Size(max=254) String contactEmail,@Size(max=1000) String justification) { }
    @Schema(name="SupplierUpdateRequest")
    public record SupplierUpdateRequest(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=255) String name,@Size(max=80) String document,@Schema(types={"string","null"}) @Size(max=120) String contactName,@Schema(types={"string","null"}) @Size(max=40) String contactPhone,
            @Schema(types={"string","null"}) @Email @Size(max=254) String contactEmail,@Size(max=1000) String justification) { }
    @Schema(name="FinancialCategoryCreateRequest")
    public record FinancialCategoryCreateRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,49}") String code,
            @NotBlank @Size(max=255) String name,@Size(max=1000) String description,@NotNull FinancialCategoryType type,@Size(max=1000) String justification) { }
    @Schema(name="FinancialCategoryUpdateRequest")
    public record FinancialCategoryUpdateRequest(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=255) String name,@Size(max=1000) String description,@Size(max=1000) String justification) { }
}
