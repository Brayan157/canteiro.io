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
import java.util.List;
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
        assertCustomerAndEmployeeListFilters(openApi);
        assertFinalCustomerContactAndAddressCrud(openApi);
        assertBillingControlAmountsUseCanonicalDecimalStrings(openApi);
        assertRevenueAmountsUseCanonicalDecimalStrings(openApi);
        assertContractServiceAndDiscountContract(openApi);
        assertWorkAndContractEditingAndListFilters(openApi);
        assertMeasurementListContract(openApi);
        assertMeasurementVersionHistoryContract(openApi);
        assertMeasurementFinancialAmountsUseCanonicalDecimalStrings(openApi);
        assertMeasurementContractCompositionContract(openApi);
        assertContinuousMeasurementContract(openApi);
        assertMeasurementExportsAreBinaryStreamingDownloads(openApi);
        assertCurrencyContract(openApi);
        assertAuditEventSchema(openApi);
        assertChangeRequestApprovalInboxContract(openApi);

        String exportPath = System.getenv("OPENAPI_EXPORT_PATH");
        if (exportPath != null && !exportPath.isBlank()) {
            Path target = Path.of(exportPath).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, openApi);
        }
    }

    private void assertCurrencyContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        assertTrue(root.at("/paths/~1api~1v1~1company~1currencies/get/responses").has("200"));
        for (String schema : List.of("CreateContractRequest", "UpdateContractRequest", "ContractResponse", "CreateMeasurementRequest",
                "MeasurementResponse", "MeasurementVersionHistoryResponse", "MeasurementContractCompositionResponse", "MeasurementFinancialSummaryResponse",
                "BillingInstallmentResponse", "ReceivableResponse", "InvoiceResponse", "TaxObligationResponse", "ReceivableBalanceResponse",
                "ReceivableSettlementResponse", "BillingControlSummaryResponse", "ContractBillingControlResponse", "ReceivableCreditResponse", "ReceivableRenegotiationResponse")) {
            JsonNode property = root.at("/components/schemas/" + schema + "/properties/currency");
            assertFalse(property.isMissingNode(), schema);
            assertTrue(property.toString().contains("BRL") && property.toString().contains("USD") && property.toString().contains("EUR"), schema);
        }
        assertTrue(root.at("/paths/~1api~1v1~1company~1measurements/post/responses").has("409"));
        for (String schema : List.of("BillingAllocationRequest", "InvoiceItemRequest", "CreateReceivableSettlementRequest", "CreateTaxObligationRequest")) {
            String field = schema.endsWith("ItemRequest") || schema.equals("BillingAllocationRequest") ? "allocatedAmount" : "amount";
            assertEquals("string", root.at("/components/schemas/" + schema + "/properties/" + field + "/type").asText());
            assertEquals("decimal", root.at("/components/schemas/" + schema + "/properties/" + field + "/format").asText());
        }
        assertTrue(root.at("/paths/~1api~1v1~1company~1contracts~1{contractId}/put/responses").has("409"));
    }

    private void assertContinuousMeasurementContract(String openApi) throws Exception {
        JsonNode d=new ObjectMapper().readTree(openApi);
        String base="/api/v1/company/measurements/{measurementId}";
        assertOperation(d,base+"/accumulated","get","200","403","404");
        assertOperation(d,base+"/services","get","200","403","404");
        assertOperation(d,base+"/services","post","201","202","400","403","404","409");
        assertOperation(d,base+"/services/{continuousServiceId}/destination","put","200","202","400","403","404","409");
        assertOperation(d,base+"/versions/{versionId}/items/{itemId}/service","put","200","202","400","403","404","409");
        assertOperation(d,base+"/contract-compositions","get","200","403","404");
        assertOperation(d,base+"/contract-compositions/apply","post","200","403","404");
        for(String suffix:List.of("pdf","spreadsheet","spreadsheet/preview")) {
            String path=base+"/accumulated/"+suffix;
            assertOperation(d,path,"get","200","403","404");
            String type=suffix.equals("spreadsheet")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/pdf";
            JsonNode response=d.path("paths").path(path).path("get").path("responses").path("200").path("content").path(type).path("schema");
            assertEquals("string",response.path("type").asText());assertEquals("binary",response.path("format").asText());
        }
        assertTrue(d.at("/components/schemas/MeasurementAccumulatedResponse/properties").has("services"));
        assertTrue(d.at("/components/schemas/MeasurementAccumulatedItemResponse/properties").has("editable"));
        for(String dto:List.of("MeasurementFinancialSummaryResponse","MeasurementFinancialServiceResponse")) {
            JsonNode properties=d.at("/components/schemas/"+dto+"/properties");
            for(String field:List.of("acceptedAmount","billedAmount","receivedAmount","acceptedAvailableToBill","billedOutstandingAmount","remainingToReceive","financialAdjustmentAmount")) {
                assertDecimalStringType(properties.path(field),dto+"."+field);
                assertEquals("decimal",properties.path(field).path("format").asText(),dto+"."+field);
            }
        }
        for(String dto:List.of("CreateMeasurementItemRequest","MeasurementItemDataRequest","ReorderMeasurementItemRequest"))
            assertTrue(d.at("/components/schemas/"+dto+"/properties").has("continuousServiceId"),dto);
        String titles="/api/v1/company/receivables/{receivableId}";
        assertOperation(d,titles+"/credits","post","201","202","400","403","404","409");
        assertOperation(d,titles+"/renegotiations","post","200","202","400","403","404","409");
        assertOperation(d,titles+"/renegotiations","get","200","403","404");
        assertOperation(d,titles+"/renegotiations/reversal","post","200","202","403","404","409");
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
        assertTrue(document.at("/components/schemas/EmployeeResponse/properties/employmentType/enum").isArray());
        assertTrue(document.at("/components/schemas/EmployeeResponse/properties/gender/enum").isArray());
        assertTrue(document.at("/components/schemas/EmployeeResponse/properties/compensationFrequency/enum").isArray());
    }

    private void assertCustomerAndEmployeeListFilters(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String customers = "/api/v1/company/customers";
        assertOperation(document, customers, "get", "200", "403");
        assertOperation(document, customers + "/{customerId}", "put", "200", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/PageResponseFinalCustomerResponse",
                responseSchemaReference(document, customers, "get", "200"));
        JsonNode customerParameters = document.at("/paths/" + escapeJsonPointer(customers) + "/get/parameters");
        for (String parameter : Set.of("name", "document", "customerType", "active")) {
            assertTrue(hasParameter(customerParameters, parameter), () -> "Customer query is missing " + parameter);
        }
        JsonNode employeeParameters = document.at("/paths/~1api~1v1~1company~1employees/get/parameters");
        for (String parameter : Set.of("name", "document", "jobTitle", "department", "employmentType",
                "compensationFrequency", "compensationMin", "compensationMax", "currentlyEmployed", "active")) {
            assertTrue(hasParameter(employeeParameters, parameter), () -> "Employee query is missing " + parameter);
        }
        JsonNode updateCustomerRequired = document.at("/components/schemas/UpdateFinalCustomerRequest/required");
        for (String field : Set.of("customerType", "name", "document")) {
            assertTrue(contains(updateCustomerRequired, field), () -> "UpdateFinalCustomerRequest must require " + field);
        }
    }

    private void assertWorkAndContractEditingAndListFilters(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String works = "/api/v1/company/works";
        String contracts = "/api/v1/company/contracts";
        assertOperation(document, works, "get", "200", "403");
        assertOperation(document, works + "/{workId}", "put", "200", "202", "400", "403", "404");
        assertEquals("#/components/schemas/PageResponseWorkResponse",
                responseSchemaReference(document, works, "get", "200"));
        JsonNode workParameters = document.at("/paths/" + escapeJsonPointer(works) + "/get/parameters");
        for (String parameter : Set.of("name", "reference", "finalCustomerId", "finalCustomerName", "finalCustomerDocument", "status", "executionLocationType",
                "startedFrom", "startedTo", "expectedCompletionFrom", "expectedCompletionTo")) {
            assertTrue(hasParameter(workParameters, parameter), () -> "Work query is missing " + parameter);
        }
        JsonNode updateWorkRequired = document.at("/components/schemas/UpdateWorkRequest/required");
        for (String field : Set.of("name", "executionLocationType", "status")) {
            assertTrue(contains(updateWorkRequired, field), () -> "UpdateWorkRequest must require " + field);
        }
        assertFalse(document.at("/components/schemas/UpdateWorkRequest/properties").has("finalCustomerId"));

        assertOperation(document, contracts, "get", "200", "403");
        assertOperation(document, contracts + "/{contractId}", "put", "200", "202", "400", "403", "404");
        assertEquals("#/components/schemas/PageResponseContractResponse",
                responseSchemaReference(document, contracts, "get", "200"));
        JsonNode contractParameters = document.at("/paths/" + escapeJsonPointer(contracts) + "/get/parameters");
        for (String parameter : Set.of("name", "reference", "workId", "workName", "finalCustomerName", "status", "startedFrom", "startedTo",
                "expectedCompletionFrom", "expectedCompletionTo", "completedFrom", "completedTo")) {
            assertTrue(hasParameter(contractParameters, parameter), () -> "Contract query is missing " + parameter);
        }
        JsonNode updateContractRequired = document.at("/components/schemas/UpdateContractRequest/required");
        for (String field : Set.of("name", "status")) {
            assertTrue(contains(updateContractRequired, field), () -> "UpdateContractRequest must require " + field);
        }
        assertFalse(document.at("/components/schemas/UpdateContractRequest/properties").has("workId"));
    }

    private void assertRevenueAmountsUseCanonicalDecimalStrings(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        JsonNode schemas = document.at("/components/schemas");
        String base = "/api/v1/company";
        for(String route : List.of("/billing-installments", "/invoices", "/invoices/{invoiceId}/tax-obligations",
                "/receivables/{receivableId}/settlements", "/receivables/{receivableId}/settlements/{settlementId}/reversal")) {
            assertOperation(document, base + route, "post", "201", "202", "400", "403", "404", "409");
            assertFalse(document.path("paths").path(base + route).path("post").path("responses").has("200"), route);
        }
        for(String route : List.of("/billing-installments/{installmentId}/cancellation", "/invoices/{invoiceId}/cancellation")) {
            assertOperation(document, base + route, "post", "200", "202", "400", "403", "404", "409");
        }
        java.util.Map<String, List<String>> fields = java.util.Map.of(
                "BillingAllocationResponse", List.of("allocatedAmount"),
                "BillingControlSummaryResponse", List.of("contractNetAmount", "activeAllocatedAmount", "availableAmount"),
                "InvoiceResponse", List.of("totalAmount"), "InvoiceItemResponse", List.of("allocatedAmount"),
                "ReceivableResponse", List.of("originalAmount"), "ReceivableSettlementResponse", List.of("amount"),
                "TaxObligationResponse", List.of("amount"),
                "ReceivableBalanceResponse", List.of("originalAmount", "withheldAmount", "creditedAmount", "netReceivableAmount",
                        "confirmedSettlementAmount", "outstandingAmount"));
        fields.forEach((schema, names) -> {
            var properties = schemas.path(schema).path("properties");
            assertCanonicalDecimalProperties(properties, names.toArray(String[]::new));
            for (String name : names) assertTrue(properties.path(name).path("pattern").asText().contains("{2}"), schema + "." + name);
        });
    }

    private void assertBillingControlAmountsUseCanonicalDecimalStrings(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String contractBillingControl = "/api/v1/company/billing-control/contracts/{contractId}";
        assertOperation(document, contractBillingControl, "get", "200");
        assertCanonicalDecimalProperties(document.at("/components/schemas/ContractBillingControlResponse/properties"),
                "serviceSubtotal", "contractDiscountAmount", "contractNetAmount", "activeAllocatedAmount", "availableAmount");
        assertCanonicalDecimalProperties(document.at("/components/schemas/BillingServiceControlResponse/properties"),
                "serviceNetAmount", "activeAllocatedAmount", "availableAmount", "issuedInInvoicesAmount");
        assertCanonicalDecimalProperties(document.at("/components/schemas/BillingAllocationControlResponse/properties"),
                "allocatedAmount", "registeredInvoiceAmount", "availableInvoiceAmount");
    }

    private void assertContractServiceAndDiscountContract(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String contract = "/api/v1/company/contracts/{contractId}";
        String services = contract + "/services";
        String service = services + "/{serviceId}";
        assertOperation(document, services, "post", "201", "202", "400", "403", "404", "409");
        assertOperation(document, service, "put", "200", "202", "400", "403", "404", "409");
        assertOperation(document, service + "/discount", "put", "200", "202", "400", "403", "404", "409");
        assertOperation(document, service + "/discount/reversal", "post", "200", "202", "400", "403", "404", "409");
        assertOperation(document, contract + "/discount", "put", "200", "202", "400", "403", "404", "409");
        assertOperation(document, contract + "/discount/reversal", "post", "200", "202", "400", "403", "404", "409");
        assertOperation(document, "/api/v1/company/service-templates", "get", "200", "403");
        assertCanonicalDecimalProperties(document.at("/components/schemas/ContractServiceResponse/properties"),
                "quantity", "unitPrice", "grossAmount", "discountValue", "discountAmount", "netAmount");
        assertTrue(document.at("/components/schemas/ContractResponse/properties").has("contractIdentifier"));
        assertTrue(document.at("/components/schemas/UpsertContractServiceRequest/properties").has("sourceServiceTemplateId"));
        assertTrue(document.at("/components/schemas/ReverseDiscountRequest/required").toString().contains("justification"));
        assertFalse(document.at("/components/schemas/UpsertContractServiceRequest/properties").has("contractId"));
    }

    private void assertCanonicalDecimalProperties(JsonNode properties, String... names) {
        for (String name : names) {
            JsonNode property = properties.path(name);
            JsonNode type = property.path("type");
            boolean decimalString = type.isArray()
                    ? contains(type, "string")
                    : "string".equals(type.asText());
            assertTrue(decimalString, () -> name + " must be a decimal string");
            assertEquals("decimal", property.path("format").asText(), () -> name + " must declare decimal format");
            assertTrue(property.path("example").asText().matches("^-?\\d+\\.\\d+$"),
                    () -> name + " must use a canonical decimal example");
        }
    }

    private void assertDecimalStringType(JsonNode property,String label) {
        JsonNode type=property.path("type");
        assertTrue(type.isArray()?contains(type,"string"):"string".equals(type.asText()),label);
        assertFalse(contains(type,"number")||contains(type,"integer"),label+" must never use a binary JSON number");
    }

    private void assertFinalCustomerContactAndAddressCrud(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String customer = "/api/v1/company/customers/{customerId}";
        String contacts = customer + "/contacts";
        String contact = contacts + "/{contactId}";
        String contactPrimary = contact + "/primary";
        String addresses = customer + "/addresses";
        String address = addresses + "/{addressId}";
        String addressPrimary = address + "/primary";

        assertOperation(document, contacts, "get", "200", "403", "404");
        assertOperation(document, contacts, "post", "201", "202", "400", "403", "404");
        assertOperation(document, contact, "put", "200", "202", "400", "403", "404");
        assertOperation(document, contactPrimary, "put", "200", "202", "403", "404");
        assertOperation(document, contact, "delete", "204", "202", "403", "404");
        assertOperation(document, addresses, "get", "200", "403", "404");
        assertOperation(document, addresses, "post", "201", "202", "400", "403", "404");
        assertOperation(document, address, "put", "200", "202", "400", "403", "404");
        assertOperation(document, addressPrimary, "put", "200", "202", "403", "404");
        assertOperation(document, address, "delete", "204", "202", "403", "404");

        assertEquals("array", document.at("/paths/" + escapeJsonPointer(contacts)
                + "/get/responses/200/content/application~1json/schema/type").asText());
        assertEquals("#/components/schemas/FinalCustomerContactResponse", document.at("/paths/"
                + escapeJsonPointer(contacts) + "/get/responses/200/content/application~1json/schema/items/$ref").asText());
        assertEquals("array", document.at("/paths/" + escapeJsonPointer(addresses)
                + "/get/responses/200/content/application~1json/schema/type").asText());
        assertEquals("#/components/schemas/FinalCustomerAddressResponse", document.at("/paths/"
                + escapeJsonPointer(addresses) + "/get/responses/200/content/application~1json/schema/items/$ref").asText());
        assertTrue(document.at("/components/schemas/FinalCustomerContactRequest/required").toString().contains("name"));
        JsonNode addressRequired = document.at("/components/schemas/FinalCustomerAddressRequest/required");
        for (String field : Set.of("street", "city", "state")) {
            assertTrue(contains(addressRequired, field), () -> "FinalCustomerAddressRequest must require " + field);
        }
    }

    private void assertMeasurementExportsAreBinaryStreamingDownloads(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/measurements/{measurementId}/versions/{versionId}";
        String pdf = base + "/pdf";
        String spreadsheet = base + "/spreadsheet";
        String spreadsheetPreview = spreadsheet + "/preview";
        assertOperation(document, pdf, "get", "200", "403", "404");
        assertOperation(document, spreadsheet, "get", "200", "403", "404");
        assertOperation(document, spreadsheetPreview, "get", "200", "403", "404");
        assertEquals("binary", document.at("/paths/" + escapeJsonPointer(pdf)
                + "/get/responses/200/content/application~1pdf/schema/format").asText());
        assertEquals("binary", document.at("/paths/" + escapeJsonPointer(spreadsheet)
                + "/get/responses/200/content/application~1vnd.openxmlformats-officedocument.spreadsheetml.sheet/schema/format")
                .asText());
        assertEquals("binary", document.at("/paths/" + escapeJsonPointer(spreadsheetPreview)
                + "/get/responses/200/content/application~1pdf/schema/format").asText());
    }

    private void assertMeasurementListContract(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String measurements = "/api/v1/company/measurements";
        assertOperation(document, measurements, "get", "200", "400", "403");
        assertOperation(document, measurements, "post", "201", "202", "400", "403", "404");
        assertEquals("#/components/schemas/MeasurementChangeResponse",
                responseSchemaReference(document, measurements, "post", "201"));
        String details = "/api/v1/company/measurements/{measurementId}";
        assertOperation(document, details, "get", "200", "403", "404");
        assertOperation(document, details, "delete", "202", "204", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementCancellationResponse",
                responseSchemaReference(document, details, "delete", "202"));
        assertEquals("#/components/schemas/MeasurementDetailsResponse",
                responseSchemaReference(document, details, "get", "200"));
        assertEquals("#/components/schemas/PageResponseMeasurementResponse",
                responseSchemaReference(document, measurements, "get", "200"));
        JsonNode parameters = document.at("/paths/" + escapeJsonPointer(measurements) + "/get/parameters");
        for (String parameter : Set.of("page", "size", "sort", "workId", "contractId", "reference", "status", "measuredOnFrom", "measuredOnTo")) {
            assertTrue(hasParameter(parameters, parameter), () -> "Measurement list query is missing " + parameter);
        }
        JsonNode createMeasurement = document.at("/components/schemas/CreateMeasurementRequest");
        assertTrue(contains(createMeasurement.path("required"), "title"), "CreateMeasurementRequest must require title");
        assertTrue(!contains(createMeasurement.path("required"), "workId"), "CreateMeasurementRequest workId must be optional");
        assertTrue(!contains(createMeasurement.path("required"), "contractId"), "CreateMeasurementRequest contractId must be optional");
        assertEquals("string", createMeasurement.at("/properties/title/type").asText());
        assertEquals(1, createMeasurement.at("/properties/title/minLength").asInt());
        assertEquals(200, createMeasurement.at("/properties/title/maxLength").asInt());
        assertEquals("string", document.at("/components/schemas/MeasurementResponse/properties/title/type").asText());
        String link = "/api/v1/company/measurements/{measurementId}/link";
        assertOperation(document, link, "put", "200", "202", "400", "403", "404", "409");
        JsonNode linkRequest = document.at("/components/schemas/LinkMeasurementRequest");
        assertTrue(contains(linkRequest.path("required"), "workId"), "LinkMeasurementRequest must require workId");
        assertTrue(!contains(linkRequest.path("required"), "contractId"), "LinkMeasurementRequest contractId must be optional");
        String workflow = "/api/v1/company/measurements/{measurementId}/versions/{versionId}/workflow";
        assertOperation(document, workflow, "post", "200", "202", "400", "403", "404", "409");
        JsonNode workflowRequest = document.at("/components/schemas/AdvanceMeasurementWorkflowRequest/properties");
        assertTrue(workflowRequest.has("correctionItemIds"));
        assertEquals("array", workflowRequest.at("/correctionItemIds/type").asText());
        String revision = "/api/v1/company/measurements/{measurementId}/revisions";
        assertOperation(document, revision, "post", "201", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementRevisionResponse",
                responseSchemaReference(document, revision, "post", "201"));
        assertEquals("array", document.at("/components/schemas/MeasurementRevisionResponse/properties/items/type").asText());
        assertEquals("array", document.at("/components/schemas/MeasurementVersionResponse/properties/correctionItemIds/type").asText());
        assertEquals("#/components/schemas/MeasurementCorrectionRequestResponse",
                document.at("/components/schemas/MeasurementDetailsResponse/properties/correctionRequest/$ref").asText());
    }

    private void assertMeasurementVersionHistoryContract(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/measurements/{measurementId}/versions";
        String version = base + "/{versionId}";
        assertOperation(document, base, "get", "200", "403", "404");
        assertOperation(document, version, "get", "200", "403", "404");
        assertEquals("array", document.at("/paths/" + escapeJsonPointer(base)
                + "/get/responses/200/content/application~1json/schema/type").asText());
        assertEquals("#/components/schemas/MeasurementVersionHistoryResponse", document.at("/paths/"
                + escapeJsonPointer(base) + "/get/responses/200/content/application~1json/schema/items/$ref").asText());
        assertEquals("#/components/schemas/MeasurementVersionDetailsResponse",
                responseSchemaReference(document, version, "get", "200"));
        JsonNode history = document.at("/components/schemas/MeasurementVersionHistoryResponse/properties");
        for (String field : Set.of("id", "versionNumber", "previousVersionId", "status", "createdAt", "updatedAt",
                "totalWeightKg", "grossAmount", "discountAmount", "netAmount")) {
            assertTrue(history.has(field), () -> "Measurement version history is missing " + field);
        }
        assertCanonicalDecimalProperties(history, "totalWeightKg", "grossAmount", "discountAmount", "netAmount");
        JsonNode details = document.at("/components/schemas/MeasurementVersionDetailsResponse/properties");
        assertEquals("#/components/schemas/MeasurementVersionResponse", details.at("/version/$ref").asText());
        assertEquals("array", details.at("/items/type").asText());
        assertEquals("array", details.at("/services/type").asText());
        assertCanonicalDecimalProperties(details, "totalWeightKg", "grossAmount", "discountAmount", "netAmount");
    }

    private void assertMeasurementFinancialAmountsUseCanonicalDecimalStrings(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String details = "/api/v1/company/measurements/{measurementId}";
        String financialSummary = details + "/financial-summary";
        assertOperation(document, details, "get", "200", "403", "404");
        assertOperation(document, financialSummary, "get", "200", "403", "404");
        assertEquals("#/components/schemas/MeasurementFinancialSummaryResponse",
                responseSchemaReference(document, financialSummary, "get", "200"));
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementFinancialSummaryResponse/properties"),
                "acceptedAmount", "billedAmount", "receivedAmount", "acceptedAvailableToBill", "billedOutstandingAmount");
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementFinancialServiceResponse/properties"),
                "acceptedAmount", "billedAmount", "receivedAmount", "acceptedAvailableToBill", "billedOutstandingAmount");
        assertTrue(document.at("/components/schemas/MeasurementFinancialServiceResponse/properties/measurementServiceId").isObject());
        assertTrue(document.at("/components/schemas/MeasurementFinancialServiceResponse/properties/measurementContractCompositionId").isObject());
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementDetailsResponse/properties"),
                "discountValue", "totalWeightKg", "grossAmount", "discountAmount", "netAmount");
        assertEquals("array", document.at("/components/schemas/MeasurementDetailsResponse/properties/services/type").asText());
        assertEquals("#/components/schemas/MeasurementServiceSummaryResponse",
                document.at("/components/schemas/MeasurementDetailsResponse/properties/services/items/$ref").asText());
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementServiceSummaryResponse/properties"),
                "grossAmount", "totalWeightKg");
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementItemResponse/properties"),
                "unitPrice", "totalWeightKg", "totalAmount");
        assertEquals("number", document.at("/components/schemas/MeasurementItemResponse/properties/unitQuantity/type").asText());
        assertTrue(document.at("/components/schemas/MeasurementItemResponse/properties/unitQuantity/description").asText()
                .contains("operational count"));
        assertTrue(document.at("/components/schemas/MeasurementItemResponse/properties/chargeType/enum").toString().contains("UNIT"));
        JsonNode itemRequest = document.at("/components/schemas/CreateMeasurementItemRequest");
        assertCanonicalDecimalProperties(itemRequest.path("properties"),
                "unitPrice", "previewTotalWeightKg", "previewTotalAmount");
        for (String required : Set.of("serviceTitle", "activity", "measuredOn", "chargeType", "unitPrice")) {
            assertTrue(contains(itemRequest.path("required"), required),
                    () -> "CreateMeasurementItemRequest must require " + required);
        }
        assertEquals("number", itemRequest.at("/properties/unitQuantity/type").asText());
        assertTrue(itemRequest.at("/properties/unitQuantity/description").asText().contains("operational count"));
        assertTrue(document.at("/components/schemas/UpdateMeasurementItemRequest/properties/unitQuantity").isObject());
        assertTrue(document.at("/components/schemas/CorrectAcceptedMeasurementItemRequest/properties/unitQuantity").isObject());
        String itemPath = "/api/v1/company/measurements/{measurementId}/versions/{versionId}/items";
        assertOperation(document, itemPath, "post", "201", "202", "400", "403", "404");
        assertEquals("#/components/schemas/MeasurementItemChangeResponse",
                responseSchemaReference(document, itemPath, "post", "201"));
        String item = "/api/v1/company/measurements/{measurementId}/versions/{versionId}/items/{itemId}";
        assertOperation(document, item, "put", "200", "202", "400", "403", "404", "409");
        assertOperation(document, item, "delete", "200", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementItemCorrectionResponse",
                responseSchemaReference(document, item, "put", "200"));
        assertEquals("#/components/schemas/MeasurementItemCorrectionResponse",
                responseSchemaReference(document, item, "delete", "200"));
        String insert = itemPath + "/insert";
        assertOperation(document, insert, "post", "201", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementItemCorrectionResponse",
                responseSchemaReference(document, insert, "post", "201"));
        JsonNode insertProperties = document.at("/components/schemas/InsertMeasurementItemRequest/properties");
        assertTrue(insertProperties.has("insertAfterItemId"));
        assertEquals("#/components/schemas/MeasurementItemDataRequest", insertProperties.at("/item/$ref").asText());
        assertTrue(document.at("/components/schemas/MeasurementItemDataRequest/properties").has("unitQuantity"));
        String reorder = itemPath + "/reorder";
        assertOperation(document, reorder, "put", "200", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementItemReorderResponse",
                responseSchemaReference(document, reorder, "put", "200"));
        JsonNode reorderRequest = document.at("/components/schemas/ReorderMeasurementItemsRequest");
        assertTrue(contains(reorderRequest.path("required"), "items"));
        assertEquals("array", reorderRequest.at("/properties/items/type").asText());
        assertEquals("#/components/schemas/ReorderMeasurementItemRequest",
                reorderRequest.at("/properties/items/items/$ref").asText());
        JsonNode reorderItem = document.at("/components/schemas/ReorderMeasurementItemRequest");
        assertTrue(contains(reorderItem.path("required"), "itemId"));
        assertTrue(contains(reorderItem.path("required"), "serviceTitle"));
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementItemReorderResponse/properties"),
                "totalWeightKg", "grossAmount", "discountAmount", "netAmount");
        String correctiveRevision = item + "/corrective-revision";
        assertOperation(document, correctiveRevision, "post", "201", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementItemCorrectionResponse",
                responseSchemaReference(document, correctiveRevision, "post", "201"));
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementItemCorrectionResponse/properties"),
                "totalWeightKg", "grossAmount", "discountAmount", "netAmount");
        String importPreview = itemPath + "/import/preview";
        String importConfirm = itemPath + "/import/confirm";
        assertOperation(document, importPreview, "post", "200", "400", "403", "404", "409");
        assertOperation(document, importConfirm, "post", "200", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/MeasurementSpreadsheetImportPreviewResponse",
                responseSchemaReference(document, importPreview, "post", "200"));
        assertEquals("#/components/schemas/MeasurementSpreadsheetImportApplyResponse",
                responseSchemaReference(document, importConfirm, "post", "200"));
        assertTrue(document.at("/components/schemas/MeasurementSpreadsheetImportPreviewResponse/properties/spreadsheetHash").isObject());
        assertTrue(document.at("/components/schemas/MeasurementSpreadsheetImportPreviewResponse/properties/lineErrors").isObject());
    }

    private void assertMeasurementContractCompositionContract(String openApi) throws Exception {
        JsonNode document = new ObjectMapper().readTree(openApi);
        String services = "/api/v1/company/measurements/{measurementId}/versions/{versionId}/services";
        String composition = services + "/{measurementServiceId}/contract-compositions";
        assertOperation(document, services, "get", "200", "403", "404");
        assertOperation(document, composition, "post", "200", "201", "202", "400", "403", "404", "409");
        JsonNode request = document.at("/components/schemas/ComposeMeasurementServiceRequest");
        assertTrue(contains(request.path("required"), "action"));
        assertTrue(contains(request.path("required"), "contractId"));
        assertTrue(document.at("/components/schemas/MeasurementContractCompositionResponse/properties/measurementServiceId").isObject());
        assertCanonicalDecimalProperties(document.at("/components/schemas/MeasurementContractCompositionResponse/properties"),
                "grossAmount", "netAmount", "totalWeightKg");
        assertTrue(document.at("/components/schemas/ContractResponse/properties/pricingMode/enum").toString()
                .contains("MEASUREMENT_BASED"));
        assertCanonicalDecimalProperties(document.at("/components/schemas/ContractServiceResponse/properties"),
                "measurementCompositionAmount", "effectiveNetAmount");
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
        String response = "/paths/" + escapeJsonPointer(path) + "/" + method + "/responses/" + status + "/content/";
        JsonNode jsonReference = document.at(response + "application~1json/schema/$ref");
        return jsonReference.isTextual()
                ? jsonReference.asText()
                : document.at(response + "*~1*/schema/$ref").asText();
    }

    private String escapeJsonPointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
