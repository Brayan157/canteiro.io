package com.renovar.canteiro.io;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "springdoc.api-docs.path=/v3/api-docs",
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
    void v3ApiDocsDocumentsEveryControllerRouteAndCanExportTheCurrentContract() throws Exception {
        String openApi = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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
        assertTrue(openApi.contains("\"application/problem+json\""));
        assertTrue(openApi.contains("\"CatalogPriceResponse\""));
        assertTrue(openApi.contains("\"amount\":{\"type\":\"string\""));
        assertEmployeeAndAccessUserSchemas(openApi);
        assertEmployeeProfilePathsAndSchemas(openApi);
        assertAuditEventSchema(openApi);
        assertChangeRequestApprovalInboxContract(openApi);

        String exportPath = System.getenv("OPENAPI_EXPORT_PATH");
        if (exportPath != null && !exportPath.isBlank()) {
            Path target = Path.of(exportPath).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, openApi);
        }
    }

    private void assertEmployeeAndAccessUserSchemas(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        assertEquals(
                "#/components/schemas/PageResponseEmployeeResponse",
                responseSchemaReference(document, "/api/v1/company/employees", "get", "200")
        );
        assertEquals(
                "#/components/schemas/PageResponseAccessUserResponse",
                responseSchemaReference(document, "/api/v1/company/access/users", "get", "200")
        );
        JsonNode employeeProperties = document.at("/components/schemas/EmployeeResponse/properties");
        assertTrue(employeeProperties.has("fullName"));
        assertTrue(employeeProperties.has("jobTitle"));
        assertTrue(employeeProperties.has("documentType"));
        assertTrue(employeeProperties.has("documentNumber"));
        assertFalse(employeeProperties.has("documentNumberMasked"));
        assertTrue(employeeProperties.has("employmentStartDate"));
        assertEquals("string", employeeProperties.at("/compensationAmount/type").asText());
        assertFalse(employeeProperties.has("email"));
        JsonNode createEmployeeRequired = document.at("/components/schemas/CreateEmployeeRequest/required");
        assertTrue(contains(createEmployeeRequired, "documentType"));
        assertTrue(contains(createEmployeeRequired, "documentNumber"));
        JsonNode accessUserProperties = document.at("/components/schemas/AccessUserResponse/properties");
        assertTrue(accessUserProperties.has("email"));
        assertTrue(accessUserProperties.has("roleIds"));
        assertTrue(accessUserProperties.has("companyOwner"));
        assertFalse(accessUserProperties.has("fullName"));
        assertTrue(document.at("/components/schemas/RoleResponse/properties").has("systemProtected"));
    }

    private void assertEmployeeProfilePathsAndSchemas(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String employees = "/api/v1/company/employees";
        String employee = employees + "/{employeeId}";
        String addresses = employee + "/addresses";
        String address = addresses + "/{addressId}";
        String primaryAddress = address + "/primary";
        String paymentAccounts = employee + "/payment-accounts";
        String paymentAccount = paymentAccounts + "/{accountId}";
        String paymentDetailsReveal = paymentAccount + "/payment-details/reveal";
        String employeePhoto = employee + "/photo";

        assertOperation(document, employees, "post", "201", "202", "400", "403");
        assertOperation(document, employee, "get", "200", "403", "404");
        assertOperation(document, employee, "put", "200", "400", "403", "404");
        assertOperation(document, addresses, "post", "201", "400", "403", "404");
        assertOperation(document, addresses, "get", "200", "403", "404");
        assertOperation(document, address, "put", "200", "400", "403", "404");
        assertOperation(document, primaryAddress, "put", "200", "403", "404");
        assertOperation(document, address, "delete", "204", "403", "404");
        assertOperation(document, paymentAccounts, "post", "201", "400", "403", "404");
        assertOperation(document, paymentAccounts, "get", "200", "403", "404");
        assertOperation(document, paymentDetailsReveal, "post", "200", "403", "404");
        assertOperation(document, paymentAccount, "put", "200", "400", "403", "404");
        assertOperation(document, paymentAccount, "delete", "204", "403", "404");
        assertOperation(document, employeePhoto, "post", "200", "400", "403", "404");
        assertOperation(document, employeePhoto, "get", "200", "403", "404");
        assertOperation(document, employeePhoto, "delete", "204", "403", "404");

        JsonNode createRequired = document.at("/components/schemas/CreateEmployeeRequest/required");
        JsonNode updateRequired = document.at("/components/schemas/UpdateEmployeeRequest/required");
        for (String field : Set.of("employmentType", "currentlyEmployed", "gender", "compensationAmount",
                "compensationFrequency", "documentType", "documentNumber", "birthDate")) {
            assertTrue(contains(createRequired, field), () -> "CreateEmployeeRequest must require " + field);
            assertTrue(contains(updateRequired, field), () -> "UpdateEmployeeRequest must require " + field);
        }

        JsonNode addressProperties = document.at("/components/schemas/EmployeeAddressResponse/properties");
        assertTrue(addressProperties.has("primaryAddress"));
        assertTrue(addressProperties.has("active"));
        assertTrue(document.at("/components/schemas/EmployeeResponse/properties/photoUrl").isObject());
        JsonNode paymentProperties = document.at("/components/schemas/EmployeePaymentAccountResponse/properties");
        assertTrue(paymentProperties.has("accountNumberMasked"));
        assertTrue(paymentProperties.has("pixKeyMasked"));
        assertFalse(paymentProperties.has("accountNumber"));
        assertFalse(paymentProperties.has("pixKey"));
        JsonNode paymentRequestProperties = document.at("/components/schemas/EmployeePaymentAccountRequest/properties");
        assertTrue(paymentRequestProperties.has("accountCheckDigit"));
        assertTrue(paymentRequestProperties.at("/accountCheckDigit/writeOnly").asBoolean());
        JsonNode revealedPaymentProperties = document.at("/components/schemas/EmployeePaymentDetailsResponse/properties");
        assertTrue(revealedPaymentProperties.has("accountNumber"));
        assertTrue(revealedPaymentProperties.has("accountCheckDigit"));
        assertTrue(revealedPaymentProperties.has("pixKey"));
        assertTrue(revealedPaymentProperties.has("pixCopyPaste"));
        assertTrue(revealedPaymentProperties.has("pixQrCode"));
        assertTrue(paymentRequestProperties.has("pixReceiverName"));
        assertTrue(paymentRequestProperties.has("pixReceiverCity"));
        assertTrue(document.at("/components/schemas/PermissionResponse/properties/action/enum").toString()
                .contains("VIEW_PAYMENT_DETAILS"));
        assertTrue(document.at("/components/schemas/EmployeeEmploymentType/enum").isArray());
        assertTrue(document.at("/components/schemas/EmployeeGender/enum").isArray());
        assertTrue(document.at("/components/schemas/EmployeeCompensationFrequency/enum").isArray());
    }

    private void assertOperation(JsonNode document, String path, String method, String... expectedStatuses) {
        JsonNode operation = document.at("/paths/" + escapeJsonPointer(path) + "/" + method);
        assertTrue(operation.isObject(), () -> "OpenAPI is missing " + method.toUpperCase() + " " + path);
        for (String statusCode : expectedStatuses) {
            assertTrue(operation.at("/responses/" + statusCode).isObject(),
                    () -> method.toUpperCase() + " " + path + " must document HTTP " + statusCode);
        }
        assertEquals("#/components/responses/ProblemDetail", operation.at("/responses/default/$ref").asText());
    }

    private boolean contains(JsonNode values, String value) {
        for (JsonNode candidate : values) {
            if (value.equals(candidate.asText())) {
                return true;
            }
        }
        return false;
    }

    private void assertAuditEventSchema(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        assertEquals(
                "#/components/schemas/PageResponseAuditEventResponse",
                responseSchemaReference(document, "/api/v1/company/audit-events", "get", "200")
        );
        JsonNode properties = document.at("/components/schemas/AuditEventResponse/properties");
        assertTrue(properties.has("id"));
        assertTrue(properties.has("occurredAt"));
        assertTrue(properties.has("actorType"));
        assertTrue(properties.has("actorUserId"));
        assertTrue(properties.has("module"));
        assertTrue(properties.has("action"));
        assertTrue(properties.has("entityType"));
        assertTrue(properties.has("entityId"));
        assertTrue(properties.has("metadata"));
        assertTrue(properties.has("actorDisplayName"));
        assertTrue(properties.has("actorEmail"));
        assertTrue(properties.get("actorDisplayName").get("description").asText().contains("human-readable"));
        assertTrue(properties.has("entityDisplayName"));
        assertTrue(properties.has("entityDisplayReference"));
        assertTrue(properties.has("summary"));
        assertTrue(properties.has("origin"));
        assertTrue(properties.has("supportContext"));
        assertTrue(properties.has("correlationId"));
        assertFalse(properties.has("beforeData"));
        assertFalse(properties.has("afterData"));
    }

    private void assertChangeRequestApprovalInboxContract(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String path = "/api/v1/company/change-requests";
        assertOperation(document, path, "get", "200");
        assertOperation(document, path + "/{id}/approve", "post", "200", "400", "403", "404", "409");
        assertEquals(
                "#/components/schemas/PageResponseChangeRequestResponse",
                responseSchemaReference(document, path, "get", "200")
        );
        JsonNode parameters = document.at("/paths/" + escapeJsonPointer(path) + "/get/parameters");
        for (String expected : Set.of("search", "module", "operation", "requesterUserId", "createdFrom", "createdTo")) {
            assertTrue(hasParameter(parameters, expected), () -> "Change request query is missing " + expected);
        }
        JsonNode responseProperties = document.at("/components/schemas/ChangeRequestResponse/properties");
        assertTrue(responseProperties.has("requester"));
        assertTrue(responseProperties.has("entity"));
        assertTrue(responseProperties.has("proposedSummary"));
        JsonNode requesterProperties = document.at("/components/schemas/ChangeRequestRequesterResponse/properties");
        assertTrue(requesterProperties.has("id"));
        assertTrue(requesterProperties.has("displayName"));
        assertTrue(requesterProperties.has("employeeId"));
        assertTrue(requesterProperties.has("photoUrl"));
        JsonNode entityProperties = document.at("/components/schemas/ChangeRequestEntityResponse/properties");
        assertTrue(entityProperties.has("type"));
        assertTrue(entityProperties.has("id"));
        assertTrue(entityProperties.has("displayName"));
        assertTrue(document.at("/components/schemas/ChangeRequestDecisionResponse/properties/appliedEntity").isObject());
    }

    private boolean hasParameter(JsonNode parameters, String expectedName) {
        for (JsonNode parameter : parameters) {
            if (expectedName.equals(parameter.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private String responseSchemaReference(JsonNode document, String path, String method, String status) {
        return document.at("/paths/" + escapeJsonPointer(path) + "/" + method + "/responses/" + status
                        + "/content/*~1*/schema/$ref")
                .asText();
    }

    private String escapeJsonPointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
