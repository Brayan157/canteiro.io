package com.renovar.canteiro.io.receivables.api;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;

/** The actual multipart body is an object containing file, not a naked binary stream. */
@Configuration
public class InvoiceXmlOpenApiConfiguration {
    @Bean OpenApiCustomizer invoiceXmlMultipartContract(){return api->{
        if(api.getPaths()==null)return;
        var path=api.getPaths().get("/api/v1/company/invoices/xml/preview");
        if(path==null||path.getPost()==null||path.getPost().getRequestBody()==null)return;
        var request=path.getPost().getRequestBody();
        var body=request.getContent().get("multipart/form-data");
        if(body!=null){var schema=new ObjectSchema();schema.addProperty("file",new StringSchema().format("binary"));schema.addRequiredItem("file");body.setSchema(schema);request.setRequired(true);}
    };}
}
