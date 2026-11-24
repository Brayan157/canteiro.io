package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.FinancialCategory;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import com.renovar.canteiro.io.payables.domain.Supplier;
import com.renovar.canteiro.io.payables.application.PayableCatalogService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public final class PayableCatalogResponses {
    private PayableCatalogResponses() { }
    @Schema(name="SupplierResponse",requiredProperties={"id","name","active","version","createdAt","updatedAt"})
    public record SupplierResponse(UUID id,String name,@Schema(types={"string","null"},description="Optional normalized supplier identifier; not a CPF/CNPJ validation service.") String document,
            @Schema(types={"string","null"},maxLength=120) String contactName,
            @Schema(types={"string","null"},maxLength=40) String contactPhone,
            @Schema(types={"string","null"},format="email",maxLength=254) String contactEmail,boolean active,long version,Instant createdAt,Instant updatedAt) {
        public static SupplierResponse from(Supplier s){return s==null?null:new SupplierResponse(s.id(),s.name(),s.document(),s.contactName(),s.contactPhone(),s.contactEmail(),s.active(),s.version(),s.createdAt(),s.updatedAt());}
    }
    @Schema(name="FinancialCategoryResponse",requiredProperties={"id","code","name","type","active","version","createdAt","updatedAt"})
    public record FinancialCategoryResponse(UUID id,String code,String name,@Schema(types={"string","null"}) String description,
            @Schema(description="Immutable expense classification. Does not replace or infer WORK/COMPANY scope.") FinancialCategoryType type,
            boolean active,long version,Instant createdAt,Instant updatedAt) {
        public static FinancialCategoryResponse from(FinancialCategory c){return c==null?null:new FinancialCategoryResponse(c.id(),c.code(),c.name(),c.description(),c.type(),c.active(),c.version(),c.createdAt(),c.updatedAt());}
    }
    @Schema(name="SupplierChangeResponse",description="In 202 supplier is null; only the proposal exists.")
    public record SupplierChangeResponse(SupplierResponse supplier,@Schema(types={"string","null"},format="uuid") UUID changeRequestId,ChangeAuthorizationMode mode) {
        public static SupplierChangeResponse from(PayableCatalogService.Result r){return new SupplierChangeResponse(SupplierResponse.from(r.supplier()),r.changeRequest()==null?null:r.changeRequest().getId(),r.mode());}
    }
    @Schema(name="FinancialCategoryChangeResponse",description="In 202 category is null; only the proposal exists.")
    public record FinancialCategoryChangeResponse(FinancialCategoryResponse category,@Schema(types={"string","null"},format="uuid") UUID changeRequestId,ChangeAuthorizationMode mode) {
        public static FinancialCategoryChangeResponse from(PayableCatalogService.Result r){return new FinancialCategoryChangeResponse(FinancialCategoryResponse.from(r.category()),r.changeRequest()==null?null:r.changeRequest().getId(),r.mode());}
    }
}
