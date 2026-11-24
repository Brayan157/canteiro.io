package com.renovar.canteiro.io.payables.api;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration
public class PayableCatalogOpenApiConfiguration {
    @Bean
    OpenApiCustomizer payableCatalogNullableApprovalResults(){
        return api->{
            if(api.getComponents()==null||api.getComponents().getSchemas()==null)return;
            for(String[] field:new String[][]{{"SupplierChangeResponse","supplier","SupplierResponse"},{"FinancialCategoryChangeResponse","category","FinancialCategoryResponse"}}){
                Schema<?> response=api.getComponents().getSchemas().get(field[0]);
                if(response!=null&&response.getProperties()!=null){
                    // In 3.1 a nullable $ref is an explicit union, not type:null with a sibling $ref.
                    Schema<?> nullable=new Schema<>().addAnyOfItem(new Schema<>().$ref("#/components/schemas/"+field[2]))
                            .addAnyOfItem(new Schema<>().types(Set.of("null")));
                    response.getProperties().put(field[1],nullable);
                }
            }
            for(String[] nullable:new String[][]{{"PayablePaymentInstructionResponse","type"},{"PayablePaymentDetails","pixKeyType"},{"PayablePaymentDetails","accountType"}}){
                Schema<?> owner=api.getComponents().getSchemas().get(nullable[0]);
                if(owner!=null&&owner.getProperties()!=null&&owner.getProperties().get(nullable[1])!=null){
                    Schema<?> value=owner.getProperties().get(nullable[1]);
                    owner.getProperties().put(nullable[1],new Schema<>().addAnyOfItem(value).addAnyOfItem(new Schema<>().types(Set.of("null"))));
                }
            }
            Schema<?> creation=api.getComponents().getSchemas().get("PayableCreationResponse");
            if(creation!=null&&creation.getProperties()!=null){
                Schema<?> array=new io.swagger.v3.oas.models.media.ArraySchema().items(new Schema<>().$ref("#/components/schemas/PayableResponse"));
                creation.getProperties().put("payables",new Schema<>().addAnyOfItem(array).addAnyOfItem(new Schema<>().types(Set.of("null"))));
            }
            Schema<?> payable=api.getComponents().getSchemas().get("PayableResponse");
            if(payable!=null&&payable.getProperties()!=null)for(String field:Set.of("work","contract","supplier","financialCategory")){
                payable.getProperties().put(field,new Schema<>().addAnyOfItem(new Schema<>().$ref("#/components/schemas/PayableReferenceResponse"))
                        .addAnyOfItem(new Schema<>().types(Set.of("null"))));
            }
        };
    }
}
