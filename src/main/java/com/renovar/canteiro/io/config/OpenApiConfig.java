package com.renovar.canteiro.io.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas("ProblemDetail", problemDetailSchema())
                        .addResponses("ProblemDetail", problemDetailResponse()))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("Canteiro.io API")
                        .version("0.0.1-SNAPSHOT")
                        .description("API REST do MVP de gestão financeira de obras para prestadores de serviço.")
                        .license(new License()
                                .name("Uso proprietário")));
    }

    @Bean
    OpenApiCustomizer problemDetailResponsesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();
                if (responses == null) {
                    responses = new ApiResponses();
                    operation.setResponses(responses);
                }
                responses.addApiResponse("default", new ApiResponse().$ref("#/components/responses/ProblemDetail"));
            }));
        };
    }

    private Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new io.swagger.v3.oas.models.media.IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", new StringSchema())
                .addProperty("correlationId", new StringSchema());
    }

    private ApiResponse problemDetailResponse() {
        return new ApiResponse()
                .description("Erro padronizado da API")
                .content(new Content().addMediaType(
                        "application/problem+json",
                        new MediaType().schema(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ProblemDetail"))
                ));
    }
}
