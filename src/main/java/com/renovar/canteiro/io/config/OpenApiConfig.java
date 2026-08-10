package com.renovar.canteiro.io.config;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ObraFlow API")
                        .version("1.0.0")
                        .description("API para gerenciamento de obras, contratos, medições, faturamento e controle financeiro.")
                        .contact(new Contact()
                                .name("Renovar Tecnologia")
                                .email("contato@obraflow.com"))
                        .license(new License()
                                .name("Proprietary")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentação")
                        .url("https://obraflow.com"));
    }
}
