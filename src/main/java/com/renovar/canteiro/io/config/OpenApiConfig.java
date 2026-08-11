package com.renovar.canteiro.io.config;
import io.swagger.v3.oas.models.OpenAPI;
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
                        .title("Canteiro.io API")
                        .version("0.0.1-SNAPSHOT")
                        .description("API REST do MVP de gestão financeira de obras para prestadores de serviço.")
                        .license(new License()
                                .name("Uso proprietário")));
    }
}
