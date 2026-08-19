package com.renovar.canteiro.io;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "springdoc.api-docs.path=/api-docs",
        "integrations.asaas.enabled=true",
        "integrations.asaas.base-url=https://api-sandbox.asaas.com/v3",
        "integrations.asaas.api-key=$aact_hmlg_openapi_test_key",
        "integrations.asaas.webhook-token=openapi-test-webhook-token",
        "integrations.asaas.user-agent=canteiro.io-openapi-test",
        "integrations.asaas.webhook-zone=America/Sao_Paulo"
})
class OpenApiContractIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void documentsEveryControllerRouteAndCanExportThePostmanImport() throws Exception {
        String openApi = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Set<String> controllerPaths = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            Class<?> beanType = handler.getBeanType();
            if (beanType.getPackageName().startsWith("com.renovar.canteiro.io")
                    && beanType.isAnnotationPresent(RestController.class)) {
                controllerPaths.addAll(mapping.getPatternValues());
            }
        });
        assertFalse(controllerPaths.isEmpty());
        controllerPaths.forEach(path -> assertTrue(openApi.contains("\"" + path + "\""),
                () -> "OpenAPI is missing controller path " + path));
        assertTrue(openApi.contains("\"bearerAuth\""));

        String exportPath = System.getenv("OPENAPI_EXPORT_PATH");
        if (exportPath != null && !exportPath.isBlank()) {
            Path target = Path.of(exportPath).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, openApi);
        }
    }
}
