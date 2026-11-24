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
        assertInvoiceAttachmentValidationContract(openApi);
        assertManualInvoiceSettlementContract(openApi);
        var reportRoot=new ObjectMapper().readTree(openApi);
        assertOperation(reportRoot,"/api/v1/company/invoices/xml/preview","post","200","400","401","403","404","413");
        assertOperation(reportRoot,"/api/v1/company/billing-installments/{billingInstallmentId}/invoice-coverage","get","200","400","401","403","404","409");
        assertEquals("string",reportRoot.at("/components/schemas/InvoiceCoverageOriginResponse/properties/amount/type").asText());
        assertEquals("string",reportRoot.at("/components/schemas/InvoiceXmlSourceValue/properties/value/type").asText());
        assertTrue(reportRoot.at("/components/schemas/CreateRegisteredInvoiceRequest/properties/documentTotalAmount/type").toString().contains("string"));
        assertTrue("100.00".matches(reportRoot.at("/components/schemas/CreateRegisteredInvoiceRequest/properties/documentTotalAmount/pattern").asText()));
        assertFalse(reportRoot.at("/components/schemas/InvoiceXmlPreviewResponse/properties").has("issuerDocument"));
        String instructionPath="/api/v1/company/payables/{payableId}/payment-instructions";
        assertOperation(reportRoot,instructionPath,"put","200","202","400","401","403","404","409");
        assertOperation(reportRoot,instructionPath,"get","200","403","404");
        assertOperation(reportRoot,instructionPath+"/reveal","post","200","403","404");
        assertOperation(reportRoot,instructionPath+"/boleto","post","200","202","400","403","404","409");
        assertOperation(reportRoot,instructionPath+"/boleto","get","200","403","404");
        assertTrue(reportRoot.at("/components/schemas/SupplierResponse/properties/contactEmail/type").toString().contains("null"));
        assertTrue(reportRoot.at("/components/schemas/PayablePaymentInstructionResponse/properties/type/anyOf").toString().contains("null"));
        assertFalse(reportRoot.at("/components/schemas/PayableResponse/properties").has("pixKey"));
        assertFalse(reportRoot.at("/components/schemas/PayablePaymentInstructionResponse/properties").has("ciphertext"));
        String reportPath="/api/v1/company/billing-control/contracts/{contractId}/report/pdf";
        assertOperation(reportRoot,reportPath,"get","200","400","401","403","404","409");
        assertEquals("binary",reportRoot.path("paths").path(reportPath).at("/get/responses/200/content/application~1pdf/schema/format").asText());
        String reportDescription=reportRoot.path("paths").path(reportPath).at("/get/description").asText();
        assertTrue(reportDescription.contains("BILLING.EXPORT"));assertTrue(reportDescription.contains("INVOICES.EXPORT"));
        assertEmployeeProfilePathsAndSchemas(openApi);
        assertCustomerAndEmployeeListFilters(openApi);
        assertFinalCustomerContactAndAddressCrud(openApi);
        assertBillingControlAmountsUseCanonicalDecimalStrings(openApi);
        assertBillableMeasurementOriginsContract(openApi);
        assertBillingInstallmentReadContract(openApi);
        assertInvoiceReadContract(openApi);
        assertInvoiceTaxReadContract(openApi);
        assertReceivableReadContract(openApi);
        assertReceivableSettlementHistoryContract(openApi);
        assertNetBillingContract(openApi);
        assertServiceNetBillingAndCalculatedTaxContract(openApi);
        assertPayableCatalogContract(openApi);
        assertOperationalPayablesContract(openApi);
        assertPayableSettlementContract(openApi);
        assertRealizedCashContract(openApi);
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
        assertWorkDiaryContract(openApi);

        String exportPath = System.getenv("OPENAPI_EXPORT_PATH");
        if (exportPath != null && !exportPath.isBlank()) {
            Path target = Path.of(exportPath).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, openApi);
        }
    }

    private void assertWorkDiaryContract(String openApi) throws Exception {
        var paths = new ObjectMapper().readTree(openApi).path("paths");
        String diary = "/api/v1/company/works/{workId}/diary";
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/{date}").path("post").path("responses").has("200"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/{date}").path("post").path("responses").has("202"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/{date}").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/{date}/revisions/{revision}").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/time-summary").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/{date}/plan").path("put").path("responses").has("202"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/{date}/comparison").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/managerial-result").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path(diary + "/operations").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path("/api/v1/company/workforce/employments").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path("/api/v1/company/workforce/assignments").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path("/api/v1/company/workforce/grants").has("get"));
        org.junit.jupiter.api.Assertions.assertTrue(paths.path("/api/v1/company/workforce/grants/{grantId}/revoke").has("put"));
    }

    private void assertServiceNetBillingAndCalculatedTaxContract(String openApi) throws Exception {
        var root = new ObjectMapper().readTree(openApi);
        String services = "/api/v1/company/billing-control/contracts/{contractId}/net-capacity/services";
        String billing = "/api/v1/company/billing-installments/net/by-service";
        String tax = "/api/v1/company/invoices/{invoiceId}/tax-obligations";
        assertOperation(root, services, "get", "200", "400", "401", "403", "404", "409");
        assertOperation(root, billing, "post", "200", "201", "202", "400", "401", "403", "404", "409");
        assertOperation(root, tax + "/preview", "post", "200", "400", "401", "403", "404", "409");
        assertOperation(root, tax + "/calculated", "post", "200", "201", "202", "400", "401", "403", "404", "409");
        assertOperation(root, tax + "/{taxObligationId}/calculation", "get", "200", "400", "401", "403", "404", "409");
        assertEquals("#/components/schemas/ServiceNetBillingCapacityResponse", responseSchemaReference(root, services, "get", "200"));
        var grouped = root.at("/components/schemas/ServiceNetBillingCapacityItemResponse/properties");
        for (String field : List.of("availableNetAmount", "baseNetAvailableAmount", "measurementNetAvailableAmount", "blockedNetAmount")) assertEquals("string", grouped.path(field).path("type").asText());
        assertTrue(grouped.has("coverageComplete")); assertTrue(grouped.has("originCount"));
        var calculation = root.at("/components/schemas/CalculateInvoiceTaxRequest/properties");
        for (String field : List.of("amount", "baseAmount", "ratePercent")) assertTrue(calculation.path(field).toString().contains("string"));
        assertTrue(calculation.path("ratePercent").path("pattern").asText().contains("1,4"));
        assertEquals("string", root.at("/components/schemas/CreateCalculatedInvoiceTaxRequest/properties/confirmedAmount/type").asText());
        assertEquals("string", root.at("/components/schemas/InvoiceTaxCalculationResponse/properties/amount/type").asText());
        for (String field : List.of("reference", "description", "issuedOn", "financialGroupId", "justification")) assertTrue(root.at("/components/schemas/CreateServiceNetBillingRequest/properties/"+field).toString().contains("\"null\""));
        for (String field : List.of("receivableId", "description", "dueOn", "justification")) assertTrue(root.at("/components/schemas/CreateCalculatedInvoiceTaxRequest/properties/"+field).toString().contains("\"null\""));
    }

    private void assertReceivableSettlementHistoryContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String path = "/api/v1/company/receivables/{receivableId}/settlements";
        assertOperation(root, path, "get", "200", "400", "401", "403", "404");
        assertOperation(root, path, "post", "201", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/PageResponseReceivableSettlementHistoryResponse", responseSchemaReference(root, path, "get", "200"));
        assertEquals("#/components/schemas/ReceivableSettlementHistoryResponse", root.at("/components/schemas/PageResponseReceivableSettlementHistoryResponse/properties/items/items/$ref").asText());
        var properties = root.at("/components/schemas/ReceivableSettlementHistoryResponse/properties");
        assertCanonicalDecimalProperties(properties, "amount");
        for (String field : List.of("receivableReference", "reversalOfSettlementId", "reversedBySettlementId")) assertTrue(properties.path(field).toString().contains("\"null\""));
        for (String field : List.of("note", "reason", "bankAccount", "pixKey", "taxes", "allocations", "beforeData", "afterData", "outstandingAmount")) assertFalse(properties.has(field));
        assertEquals(12, root.at("/components/schemas/ReceivableSettlementHistoryResponse/required").size());
        Set<String> parameters = new TreeSet<>();
        root.path("paths").path(path).path("get").path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asText()));
        assertEquals(Set.of("receivableId", "entryType", "status", "paymentMethod", "receivedFrom", "receivedTo", "page", "size", "sort"), parameters);
        assertTrue(root.path("paths").path(path).path("get").path("description").asText().contains("RECEIVABLES.READ"));
        assertTrue(properties.path("amount").path("pattern").asText().contains("^-?"));
    }

    private void assertReceivableReadContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/receivables";
        assertOperation(root, base, "get", "200", "400", "401", "403");
        assertOperation(root, base + "/{receivableId}", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/PageResponseReceivableSummaryResponse", responseSchemaReference(root, base, "get", "200"));
        assertEquals("#/components/schemas/ReceivableDetailsResponse", responseSchemaReference(root, base + "/{receivableId}", "get", "200"));
        assertEquals("#/components/schemas/ReceivableSummaryResponse", root.at("/components/schemas/PageResponseReceivableSummaryResponse/properties/items/items/$ref").asText());
        Set<String> parameters = new TreeSet<>();
        root.path("paths").path(base).path("get").path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asText()));
        assertEquals(Set.of("search", "reference", "contractSearch", "workSearch", "customerName", "billingReference", "contractId", "workId", "finalCustomerId",
                "status", "financialView", "currency", "dueFrom", "dueTo", "page", "size", "sort"), parameters);
        var views = new TreeSet<String>();
        root.path("paths").path(base).path("get").path("parameters").forEach(parameter -> {
            if (parameter.path("name").asText().equals("financialView")) parameter.path("schema").path("enum").forEach(value -> views.add(value.asText()));
        });
        assertEquals(Set.of("ALL", "OUTSTANDING", "RECEIVED"), views);
        var properties = root.at("/components/schemas/ReceivableSummaryResponse/properties");
        assertCanonicalDecimalProperties(properties, "originalAmount");
        for (String field : List.of("reference", "contractReference", "contractIdentifier", "workReference", "billingInstallmentReference", "renegotiationSourceReceivableId", "renegotiationSourceReference", "issuedOn")) {
            assertTrue(properties.path(field).toString().contains("\"null\""), field);
        }
        for (String field : List.of("document", "phone", "email", "taxes", "settlements", "allocations", "outstandingAmount", "accessKey", "storageKey")) assertFalse(properties.has(field));
        assertTrue(root.path("paths").path(base).path("get").path("description").asText().contains("RECEIVABLES.READ"));
        assertTrue(properties.path("status").toString().contains("Persisted"));
        assertEquals("#/components/schemas/ReceivableFinancialPositionResponse", properties.at("/financialPosition/$ref").asText());
        assertCanonicalDecimalProperties(root.at("/components/schemas/ReceivableFinancialPositionResponse/properties"),
                "withheldAmount", "creditedAmount", "netReceivableAmount", "confirmedSettlementAmount", "outstandingAmount");
    }

    private void assertInvoiceTaxReadContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String path = "/api/v1/company/invoices/{invoiceId}/tax-obligations";
        assertOperation(root, path, "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/PageResponseInvoiceTaxObligationResponse", responseSchemaReference(root, path, "get", "200"));
        assertEquals("#/components/schemas/InvoiceTaxObligationResponse", root.at("/components/schemas/PageResponseInvoiceTaxObligationResponse/properties/items/items/$ref").asText());
        Set<String> parameters = new TreeSet<>();
        var operation = root.path("paths").path(path).path("get");
        operation.path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asText()));
        assertEquals(Set.of("invoiceId", "status", "treatment", "page", "size", "sort"), parameters);
        assertTrue(operation.path("description").asText().contains("INVOICES.READ"));
        var properties = root.at("/components/schemas/InvoiceTaxObligationResponse/properties");
        assertCanonicalDecimalProperties(properties, "amount");
        for (String field : List.of("description", "dueOn")) assertTrue(properties.path(field).toString().contains("\"null\""));
        for (String field : List.of("receivableId", "payableId", "outstandingAmount", "accessKey", "storageKey")) assertFalse(properties.has(field));
        assertEquals(10, root.at("/components/schemas/InvoiceTaxObligationResponse/required").size());
    }

    private void assertInvoiceReadContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/invoices";
        assertOperation(root, base, "get", "200", "400", "401", "403");
        assertOperation(root, base + "/{invoiceId}", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/PageResponseInvoiceSummaryResponse", responseSchemaReference(root, base, "get", "200"));
        assertEquals("#/components/schemas/InvoiceDetailsResponse", responseSchemaReference(root, base + "/{invoiceId}", "get", "200"));
        assertOperation(root, base + "/contracts", "get", "200", "400", "401", "403");
        assertOperation(root, base + "/contracts/{contractId}", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/PageResponseInvoiceContractResponse", responseSchemaReference(root, base + "/contracts", "get", "200"));
        assertEquals("#/components/schemas/InvoiceContractResponse", responseSchemaReference(root, base + "/contracts/{contractId}", "get", "200"));
        var contractFields = root.at("/components/schemas/InvoiceContractResponse/properties");
        assertEquals(4, contractFields.size());
        for (String field : List.of("contractId", "contractName", "contractReference", "contractIdentifier")) assertTrue(contractFields.has(field));
        for (String field : List.of("contractReference", "contractIdentifier")) assertTrue(contractFields.path(field).toString().contains("\"null\""));
        Set<String> groupParameters = new TreeSet<>();
        root.path("paths").path(base + "/contracts").path("get").path("parameters").forEach(p -> groupParameters.add(p.path("name").asText()));
        assertEquals(Set.of("search", "page", "size", "sort"), groupParameters);
        assertCanonicalDecimalProperties(root.at("/components/schemas/InvoiceSummaryResponse/properties"), "totalAmount");
        assertCanonicalDecimalProperties(root.at("/components/schemas/InvoiceReadItemResponse/properties"), "allocatedAmount");
        Set<String> filters = new TreeSet<>();
        root.path("paths").path(base).path("get").path("parameters").forEach(p -> filters.add(p.path("name").asText()));
        assertEquals(Set.of("contractId", "number", "series", "contractSearch", "installmentReference", "status", "issuedFrom", "issuedTo", "page", "size", "sort"), filters);
        assertTrue(root.path("paths").path(base).path("get").path("description").asText().contains("INVOICES.READ"));
        assertFalse(root.at("/components/schemas/InvoiceSummaryResponse/properties").has("accessKey"));
        assertFalse(root.at("/components/schemas/InvoiceDetailsResponse/properties").has("attachments"));
        assertEquals("#/components/schemas/InvoiceReceiptPositionResponse", root.at("/components/schemas/InvoiceSummaryResponse/properties/receiptPosition/$ref").asText());
        var receipt = root.at("/components/schemas/InvoiceReceiptPositionResponse/properties");
        for (String field : List.of("receivedAmount", "outstandingAmount")) {
            assertTrue(receipt.path(field).toString().contains("\"null\""));
            assertEquals("decimal", receipt.path(field).path("format").asText());
        }
        for (String field : List.of("contractServiceId", "contractServiceName", "measurementContractCompositionId", "description")) {
            assertTrue(root.at("/components/schemas/InvoiceReadItemResponse/properties/" + field).toString().contains("\"null\""), field);
        }
    }

    private void assertOperationalPayablesContract(String openApi) throws Exception {
        JsonNode root=new ObjectMapper().readTree(openApi);String path="/api/v1/company/payables";
        assertOperation(root,path,"post","200","201","202","400","401","403","404","409");
        assertOperation(root,path,"get","200","400","401","403");
        assertOperation(root,path+"/{payableId}","get","200","401","403","404");
        assertEquals("#/components/schemas/PageResponsePayableResponse",responseSchemaReference(root,path,"get","200"));
        var view = root.path("paths").path(path).path("get").path("parameters").valueStream()
                .filter(parameter -> "view".equals(parameter.path("name").asText())).findFirst().orElseThrow();
        assertEquals("query", view.path("in").asText());
        assertFalse(view.path("required").asBoolean());
        assertEquals(List.of("OUTSTANDING", "HISTORY"), view.at("/schema/enum").valueStream().map(JsonNode::asText).toList());
        assertEquals("#/components/schemas/PayableResponse",responseSchemaReference(root,path+"/{payableId}","get","200"));
        assertFalse(root.at("/components/schemas/PayableCreateRequest/properties").has("companyId"));
        assertTrue(root.at("/components/schemas/PayableCreateRequest/required").toString().contains("financialScope"));
        assertCanonicalDecimalProperties(root.at("/components/schemas/PayableTitleCreateRequest/properties"),"originalAmount");
        assertCanonicalDecimalProperties(root.at("/components/schemas/PayableResponse/properties"),"originalAmount");
        var list=root.at("/components/schemas/PayableCreationResponse/properties/payables/anyOf");
        assertEquals("#/components/schemas/PayableResponse",list.at("/0/items/$ref").asText());assertEquals("null",list.at("/1/type").asText());
        for(String field:List.of("work","contract","supplier","financialCategory")){
            var union=root.at("/components/schemas/PayableResponse/properties/"+field+"/anyOf");
            assertEquals("#/components/schemas/PayableReferenceResponse",union.at("/0/$ref").asText());assertEquals("null",union.at("/1/type").asText());
        }
    }

    private void assertRealizedCashContract(String openApi) throws Exception {
        var root = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/cash-flow";
        assertOperation(root, base + "/report/pdf", "get", "200", "400", "401", "403");
        assertOperation(root, base + "/works/{workId}/report/pdf", "get", "200", "400", "401", "403", "404");
        var report = root.path("paths").path(base + "/report/pdf").path("get").path("responses").path("200");
        assertEquals("binary", report.at("/content/application~1pdf/schema/format").asText());
        assertTrue(report.path("headers").has("Content-Disposition"));
        assertOperation(root, base + "/realized", "get", "200", "400", "401", "403");
        assertOperation(root, base + "/works/{workId}/realized", "get", "200", "400", "401", "403", "404");
        String accumulated = base + "/works/{workId}/realized/accumulated";
        assertOperation(root, accumulated, "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/RealizedCashResponse", responseSchemaReference(root, accumulated, "get", "200"));
        assertEquals("#/components/schemas/RealizedCashResponse",
                responseSchemaReference(root, base + "/realized", "get", "200"));
        assertCanonicalDecimalProperties(root.at("/components/schemas/RealizedCashComponent/properties"), "amount");
        assertCanonicalDecimalProperties(root.at("/components/schemas/RealizedCashCurrencyPosition/properties"),
                "netMovement", "cashResultPercentage");
        String comparison = base + "/payments/comparison";
        assertOperation(root, comparison, "get", "200", "400", "401", "403");
        assertOperation(root, base + "/works/{workId}/payments/comparison", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/CashPaymentsComparisonResponse", responseSchemaReference(root, comparison, "get", "200"));
        assertCanonicalDecimalProperties(root.at("/components/schemas/CashPaymentsComparisonMovement/properties"),
                "differenceAmount", "percentageChange");
        assertCanonicalDecimalProperties(root.at("/components/schemas/CashPaymentsComparisonPeriodTotal/properties"), "amount");
        String byCategory = comparison + "/by-category";
        assertOperation(root, byCategory, "get", "200", "400", "401", "403");
        assertOperation(root, base + "/works/{workId}/payments/comparison/by-category", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/CashPaymentsByCategoryResponse", responseSchemaReference(root, byCategory, "get", "200"));
        assertEquals("#/components/schemas/CashPaymentsCategoryComparison",
                root.at("/components/schemas/CashPaymentsByCategoryResponse/properties/categories/items/$ref").asText());
        assertOperation(root, base + "/due", "get", "200", "400", "401", "403");
        assertOperation(root, base + "/works/{workId}/due", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/CashDueResponse", responseSchemaReference(root, base + "/due", "get", "200"));
        assertCanonicalDecimalProperties(root.at("/components/schemas/CashDueBucket/properties"), "amount");
        for (String route : List.of(base + "/due", base + "/works/{workId}/due")) {
            var parameters = root.path("paths").path(route).path("get").path("parameters");
            var horizon = parameters.valueStream().filter(p -> "horizonDays".equals(p.path("name").asText()))
                    .findFirst().orElseThrow().path("schema");
            assertEquals("integer", horizon.path("type").asText());
            assertEquals(1, horizon.path("minimum").asInt());
            assertEquals(365, horizon.path("maximum").asInt());
            assertEquals(30, horizon.path("default").asInt());
        }
    }

    private void assertPayableSettlementContract(String openApi) throws Exception {
        var root = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/payables/{payableId}";
        assertOperation(root, base + "/balance", "get", "200", "401", "403", "404");
        assertOperation(root, base + "/settlements", "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/PageResponsePayableSettlementResponse",
                responseSchemaReference(root, base + "/settlements", "get", "200"));
        for (String suffix : List.of("/settlements", "/settlements/{settlementId}/reversals")) {
            assertOperation(root, base + suffix, "post", "200", "201", "202", "400", "401", "403", "404", "409");
            assertEquals("#/components/schemas/PayableSettlementChangeResponse",
                    responseSchemaReference(root, base + suffix, "post", "201"));
            assertEquals("#/components/schemas/PendingPayableSettlementResponse",
                    responseSchemaReference(root, base + suffix, "post", "202"));
        }
        assertCanonicalDecimalProperties(root.at("/components/schemas/PayableSettlementCreateRequest/properties"), "amount");
        assertCanonicalDecimalProperties(root.at("/components/schemas/PayableSettlementResponse/properties"), "amount");
        assertEquals("#/components/schemas/PayableBalanceResponse", root.at("/components/schemas/PayableResponse/properties/balance/$ref").asText());
        for (String amount : List.of("paidAmount", "outstandingAmount", "overdueAmount")) {
            var field = root.at("/components/schemas/PayableBalanceResponse/properties/" + amount);
            assertTrue(field.path("type").valueStream().anyMatch(t -> t.asText().equals("string")));
            assertTrue(field.path("type").valueStream().anyMatch(t -> t.asText().equals("null")));
            assertEquals("decimal", field.path("format").asText());
        }
    }

    private void assertInvoiceAttachmentValidationContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String path = "/api/v1/company/invoices/{invoiceId}/attachments";
        assertOperation(root, path, "post", "200", "400", "401", "403", "404", "409", "415");
        assertEquals("#/components/schemas/InvoiceAttachmentResponse", responseSchemaReference(root, path, "post", "200"));
        var upload = root.path("paths").path(path).path("post");
        var uploadTypes = new TreeSet<String>();
        upload.path("parameters").forEach(parameter -> {
            if ("attachmentType".equals(parameter.path("name").asText())) {
                parameter.at("/schema/enum").forEach(type -> uploadTypes.add(type.asText()));
            }
        });
        assertEquals(Set.of("PDF"), uploadTypes);
        assertTrue(upload.path("description").asText().contains("INVOICE_PDF_ALREADY_ATTACHED"));
        assertTrue(upload.path("description").asText().contains("INVOICE_ATTACHMENT_PDF_REQUIRED"));
        assertTrue(root.path("paths").path("/api/v1/company/invoices/xml/preview")
                .path("post").path("description").asText().contains("DTD"));
        assertOperation(root, path, "get", "200", "400", "401", "403", "404");
        assertEquals("#/components/schemas/PageResponseInvoiceAttachmentResponse", responseSchemaReference(root, path, "get", "200"));
        Set<String> parameters = new TreeSet<>();
        root.path("paths").path(path).path("get").path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asText()));
        assertEquals(Set.of("invoiceId", "page", "size", "sort"), parameters);
        String download = path + "/{attachmentId}/download";
        assertOperation(root, download, "get", "200", "400", "401", "403", "404", "503");
        var operation = root.path("paths").path(download).path("get");
        assertTrue(operation.path("description").asText().contains("INVOICES.READ"));
        assertTrue(operation.path("description").asText().contains("INVOICES.EXPORT"));
        for (String mime : List.of("application/pdf", "application/xml")) {
            var schema = operation.path("responses").path("200").path("content").path(mime).path("schema");
            assertEquals("string", schema.path("type").asText());
            assertEquals("binary", schema.path("format").asText());
        }
        for (String header : List.of("Content-Disposition", "Cache-Control", "X-Content-Type-Options")) {
            assertTrue(operation.at("/responses/200/headers").has(header));
        }
        var properties = root.at("/components/schemas/InvoiceAttachmentResponse/properties");
        assertEquals(8, properties.size());
        assertEquals(8, root.at("/components/schemas/InvoiceAttachmentResponse/required").size());
        assertEquals("date-time", properties.path("createdAt").path("format").asText());
        for (String secret : List.of("storageKey", "path", "url", "accessKey", "content")) assertFalse(properties.has(secret));
        String cancellation = "/api/v1/company/invoices/{invoiceId}/cancellation";
        assertOperation(root, cancellation, "post", "200", "202", "400", "403", "404", "409");
        String description = root.path("paths").path(cancellation).path("post").path("description").asText();
        for (String reason : List.of("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", "INVOICE_CANCELLATION_HAS_TAX_PAYMENTS",
                "INVOICE_CANCELLATION_REQUIRES_REVIEW")) assertTrue(description.contains(reason));
    }

    private void assertManualInvoiceSettlementContract(String openApi) throws Exception {
        var root = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/receivables/{receivableId}";
        assertOperation(root, base + "/invoice-settlement-options", "get", "200", "400", "401", "403", "404");
        assertOperation(root, base + "/invoice-settlements", "post", "200", "201", "202", "400", "401", "403", "404", "409");
        assertEquals("#/components/schemas/InvoiceSettlementOptionsResponse", responseSchemaReference(root, base + "/invoice-settlement-options", "get", "200"));
        assertEquals("#/components/schemas/PendingInvoiceSettlementResponse", responseSchemaReference(root, base + "/invoice-settlements", "post", "202"));
        var pending = root.at("/components/schemas/PendingInvoiceSettlementResponse/properties");
        assertFalse(pending.has("settlement")); assertFalse(pending.has("balance")); assertFalse(pending.has("items"));
        for (String name : List.of("CreateInvoiceSettlementRequest", "InvoiceSettlementLineRequest")) {
            var amount = root.path("components").path("schemas").path(name).path("properties").path("amount");
            assertEquals("string", amount.path("type").asText()); assertEquals("decimal", amount.path("format").asText());
        }
        assertEquals(1000, root.at("/components/schemas/CreateInvoiceSettlementRequest/properties/note/maxLength").asInt());
        assertEquals("#/components/schemas/TotalInvoiceSettlementProposalResponse", root.at("/components/schemas/InvoiceSettlementOptionsResponse/properties/totalSettlementProposal/$ref").asText());
        var optionSchema = root.at("/components/schemas/InvoiceSettlementOptionResponse");
        for (String field : List.of("withheldItemAmount", "netInvoicedItemAmount", "netAvailableItemAmount")) {
            assertTrue(optionSchema.path("required").valueStream().anyMatch(name -> name.asText().equals(field)));
            var property = optionSchema.path("properties").path(field);
            assertTrue(property.path("type").valueStream().anyMatch(type -> type.asText().equals("string")));
            assertTrue(property.path("type").valueStream().anyMatch(type -> type.asText().equals("null")));
            assertEquals("decimal", property.path("format").asText());
            assertEquals("^[0-9]+\\.[0-9]{2}$", property.path("pattern").asText());
        }
        assertTrue(root.at("/components/schemas/TotalInvoiceSettlementProposalResponse/properties/amount").toString().contains("string"));
        assertEquals("decimal", root.at("/components/schemas/TotalInvoiceSettlementProposalResponse/properties/amount/format").asText());
        assertEquals("string", root.at("/components/schemas/TotalInvoiceSettlementProposalItem/properties/amount/type").asText());
        assertTrue(root.at("/components/schemas/InvoiceReceiptPositionResponse/properties/status").toString().contains("PARTIALLY_RECEIVED"));
    }

    private void assertPayableCatalogContract(String openApi) throws Exception {
        JsonNode root=new ObjectMapper().readTree(openApi);
        for (String resource:List.of("suppliers","financial-categories")) {
            String base="/api/v1/company/"+resource;
            assertOperation(root,base,"get","200","400","401","403");
            assertOperation(root,base,"post","201","202","400","401","403","409");
            assertOperation(root,base+"/{id}","put","200","202","400","401","403","404","409");
            assertOperation(root,base+"/{id}","delete","204","202","400","401","403","404","409");
            String schema=resource.equals("suppliers")?"SupplierResponse":"FinancialCategoryResponse";
            assertEquals("#/components/schemas/PageResponse"+schema,root.path("paths").path(base).at("/get/responses/200/content/application~1json/schema/$ref").asText());
            assertEquals("#/components/schemas/"+schema,root.path("paths").path(base+"/{id}").at("/get/responses/200/content/application~1json/schema/$ref").asText());
        }
        for (String request:List.of("SupplierUpdateRequest","FinancialCategoryUpdateRequest")) {
            assertTrue(root.at("/components/schemas/"+request+"/required").toString().contains("expectedVersion"));
        }
        assertFalse(root.at("/components/schemas/FinancialCategoryUpdateRequest/properties").has("code"));
        assertFalse(root.at("/components/schemas/FinancialCategoryUpdateRequest/properties").has("type"));
        for (String[] field:new String[][]{{"SupplierChangeResponse","supplier","SupplierResponse"},{"FinancialCategoryChangeResponse","category","FinancialCategoryResponse"}}) {
            var union=root.at("/components/schemas/"+field[0]+"/properties/"+field[1]+"/anyOf");
            assertEquals("#/components/schemas/"+field[2],union.at("/0/$ref").asText());
            assertEquals("null",union.at("/1/type").asText());
        }
    }

    private void assertNetBillingContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String read = "/api/v1/company/billing-control/contracts/{contractId}/net-capacity";
        String create = "/api/v1/company/billing-installments/net";
        assertOperation(root, read, "get", "200", "400", "401", "403", "404", "409");
        assertOperation(root, create, "post", "200", "201", "202", "400", "401", "403", "404", "409");
        assertEquals("#/components/schemas/NetBillingCapacityResponse",root.path("paths").path(read).at("/get/responses/200/content/application~1json/schema/$ref").asText());
        for (String field : List.of("contractNetAmount", "activeAllocatedAmount", "remainingNetAmount", "remainingDiscountAmount")) {
            JsonNode schema=root.at("/components/schemas/NetBillingCapacityResponse/properties/"+field);
            assertEquals("string",schema.path("type").asText());assertEquals("decimal",schema.path("format").asText());
        }
        for (String field : List.of("grossAvailableAmount", "netAvailableAmount", "uncommittedDiscountAmount")) {
            JsonNode schema=root.at("/components/schemas/NetBillingCapacityPositionResponse/properties/"+field);
            assertEquals("string",schema.path("type").asText());assertEquals("decimal",schema.path("format").asText());
        }
        JsonNode request=root.at("/components/schemas/CreateNetBillingRequest");
        assertEquals(1000,request.at("/properties/description/maxLength").asInt());
        for (String required : List.of("operationId", "contractId", "capacityVersion", "dueOn", "allocations")) assertTrue(request.path("required").toString().contains(required), required);
        assertFalse(root.at("/components/schemas/NetBillingAllocationRequest/properties").has("type"));
        assertEquals("string",root.at("/components/schemas/NetBillingAllocationRequest/properties/netAmount/type").asText());
        assertEquals("decimal",root.at("/components/schemas/NetBillingAllocationRequest/properties/netAmount/format").asText());
        for (String schema : List.of("BillingAllocationResponse", "BillingInstallmentAllocationResponse")) {
            for (String field : List.of("netBillingGrossAmount", "netBillingDiscountAmount")) {
                var amount=root.at("/components/schemas/"+schema+"/properties/"+field);
                assertTrue(amount.toString().contains("\"string\""));assertTrue(amount.toString().contains("\"null\""));assertEquals("decimal",amount.path("format").asText());
            }
        }
        assertTrue(root.at("/components/schemas/BillingServiceControlResponse/properties").has("netAvailableToBillAmount"));
        assertTrue(root.at("/components/schemas/BillableMeasurementOriginResponse/properties").has("netAvailableToBillAmount"));
    }

    private void assertBillingInstallmentReadContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String base = "/api/v1/company/billing-installments";
        assertOperation(root, base, "get", "200", "400", "401", "403");
        assertOperation(root, base + "/{installmentId}", "get", "200", "400", "401", "403", "404");
        JsonNode list = root.path("paths").path(base).path("get");
        assertEquals("#/components/schemas/PageResponseBillingInstallmentSummaryResponse",
                list.at("/responses/200/content/application~1json/schema/$ref").asText());
        assertEquals("#/components/schemas/BillingInstallmentSummaryResponse", root.at(
                "/components/schemas/PageResponseBillingInstallmentSummaryResponse/properties/items/items/$ref").asText());
        assertEquals("#/components/schemas/BillingInstallmentDetailsResponse", root.path("paths").path(base + "/{installmentId}")
                .path("get").at("/responses/200/content/application~1json/schema/$ref").asText());
        Set<String> filters = new TreeSet<>();
        list.path("parameters").forEach(parameter -> filters.add(parameter.path("name").asText()));
        assertEquals(Set.of("contractId", "reference", "status", "dueFrom", "dueTo", "page", "size", "sort"), filters);
        for (String schema : List.of("BillingInstallmentSummaryResponse", "BillingInstallmentDetailsResponse", "BillingInstallmentAllocationResponse")) {
            String field = schema.endsWith("AllocationResponse") ? "allocatedAmount" : "netAmount";
            JsonNode amount = root.at("/components/schemas/" + schema + "/properties/" + field);
            assertEquals("string", amount.path("type").asText(), schema);
            assertEquals("decimal", amount.path("format").asText(), schema);
        }
        assertTrue(root.at("/components/schemas/BillingInstallmentAllocationResponse/properties").has("measurementContractCompositionId"));
        assertTrue(root.at("/components/schemas/BillingInstallmentDetailsResponse/properties").has("receivableReferencesAvailable"));
        for(String schema:List.of("BillingInstallmentSummaryResponse","BillingInstallmentDetailsResponse")) {
            var model=root.at("/components/schemas/"+schema);
            for(String field:List.of("receivableFinancialStatusAvailable","receivableFinancialStatus","receivableReceivedWithWithholding")) {
                assertTrue(model.path("properties").has(field),schema+"/"+field);
                assertTrue(model.path("required").toString().contains(field),schema+"/"+field);
            }
            for(String field:List.of("receivableFinancialStatus","receivableReceivedWithWithholding"))assertTrue(model.path("properties").path(field).path("type").toString().contains("null"));
            var statuses=model.path("properties").path("receivableFinancialStatus").path("enum");
            assertTrue(statuses.valueStream().anyMatch(JsonNode::isNull));
            for(String state:List.of("OPEN","PARTIALLY_RECEIVED","RECEIVED","SETTLED_WITH_ADJUSTMENTS","CANCELLED","RENEGOTIATED"))assertTrue(statuses.valueStream().anyMatch(s->state.equals(s.asText())));
            assertTrue(model.path("properties").path("status").path("description").asText().contains("Persisted"));
        }
        assertTrue(root.path("paths").path(base).path("get").path("description").asText().contains("RECEIVABLES.READ"));

        for (String field : List.of("contractServiceId", "contractServiceName", "measurementContractCompositionId")) {
            assertTrue(root.at("/components/schemas/BillingInstallmentAllocationResponse/properties/" + field).toString().contains("\"null\""), field);
        }
        assertTrue(root.at("/components/schemas/BillingInstallmentDetailsResponse/properties/issuedOn").toString().contains("\"null\""));
        assertTrue(root.path("paths").path(base + "/{installmentId}").path("get").path("description").asText().contains("RECEIVABLES.READ"));
    }

    private void assertBillableMeasurementOriginsContract(String openApi) throws Exception {
        JsonNode root = new ObjectMapper().readTree(openApi);
        String path = "/api/v1/company/billing-control/contracts/{contractId}/measurement-origins";
        assertOperation(root, path, "get", "200", "400", "401", "403", "404");
        JsonNode operation = root.path("paths").path(path).path("get");
        assertTrue(operation.path("description").asText().contains("BILLING.READ"));
        assertTrue(operation.path("description").asText().contains("MEASUREMENTS.READ"));
        Set<String> names = new TreeSet<>();
        operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
        assertEquals(Set.of("contractId", "measurementId", "contractServiceId", "page", "size", "sort"), names);
        String pageSchema = operation.at("/responses/200/content/application~1json/schema/$ref").asText();
        assertEquals("#/components/schemas/PageResponseBillableMeasurementOriginResponse", pageSchema);
        assertEquals("#/components/schemas/BillableMeasurementOriginResponse", root.at(
                "/components/schemas/PageResponseBillableMeasurementOriginResponse/properties/items/items/$ref").asText());
        JsonNode properties = root.at("/components/schemas/BillableMeasurementOriginResponse/properties");
        for (String field : List.of("acceptedAmount", "activeAllocatedAmount", "availableToBillAmount")) {
            assertEquals("string", properties.path(field).path("type").asText(), field);
            assertEquals("decimal", properties.path(field).path("format").asText(), field);
        }
        for (String field : List.of("measurementContractCompositionId", "contractId", "contractServiceId", "measurementId",
                "measurementVersionId", "measurementServiceId", "continuousServiceId", "measurementTitle", "serviceTitle",
                "measurementReference", "versionNumber", "status", "currency", "createdAt")) {
            assertFalse(properties.path(field).isMissingNode(), field);
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
                "#/components/schemas/PageResponseEmployeeListItemResponse",
                responseSchemaReference(document, "/api/v1/company/employees", "get", "200")
        );
        JsonNode listProperties = document.at("/components/schemas/EmployeeListItemResponse/properties");
        for (String field : List.of("id", "fullName", "jobTitle", "registrationNumber", "active", "photoUrl")) {
            assertTrue(listProperties.has(field), field);
        }
        for (String field : List.of("documentType", "documentNumber", "pisMasked", "phone", "birthDate", "gender",
                "ethnicity", "compensationAmount", "compensationFrequency", "storageKey")) {
            assertFalse(listProperties.has(field), field);
        }
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
        for (String parameter : Set.of("name", "jobTitle", "department", "employmentType",
                "currentlyEmployed", "active")) {
            assertTrue(hasParameter(employeeParameters, parameter), () -> "Employee query is missing " + parameter);
        }
        for (String sensitiveParameter : Set.of("document", "compensationFrequency", "compensationMin", "compensationMax")) {
            assertFalse(hasParameter(employeeParameters, sensitiveParameter),
                    () -> "Employee query must not accept sensitive parameter " + sensitiveParameter);
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

        assertOperation(document, contracts, "get", "200", "400", "403");
        assertOperation(document, contracts, "post", "201", "202", "400", "403", "404", "409");
        for (String path : List.of(contracts, "/api/v1/company/billing-control/contracts")) {
            JsonNode parameters = document.at("/paths/" + escapeJsonPointer(path) + "/get/parameters");
            boolean documentsOpenFirst = false;
            for (JsonNode parameter : parameters) {
                if ("sort".equals(parameter.path("name").asText())) {
                    documentsOpenFirst = parameter.path("description").asText().contains("openFirst,asc");
                }
            }
            assertTrue(documentsOpenFirst, () -> "Missing openFirst sort documentation for " + path);
        }
        assertOperation(document, contracts + "/{contractId}", "put", "200", "202", "400", "403", "404", "409");
        assertEquals("#/components/schemas/PageResponseContractResponse",
                responseSchemaReference(document, contracts, "get", "200"));
        JsonNode contractParameters = document.at("/paths/" + escapeJsonPointer(contracts) + "/get/parameters");
        for (String parameter : Set.of("name", "reference", "workId", "workName", "finalCustomerName", "status", "startedFrom", "startedTo",
                "expectedCompletionFrom", "expectedCompletionTo", "completedFrom", "completedTo")) {
            assertTrue(hasParameter(contractParameters, parameter), () -> "Contract query is missing " + parameter);
        }
        JsonNode updateContractRequired = document.at("/components/schemas/UpdateContractRequest/required");
        for (String request : List.of("CreateContractRequest", "UpdateContractRequest")) {
            JsonNode schema = document.at("/components/schemas/" + request);
            assertFalse(contains(schema.path("required"), "reference"));
            assertEquals(100, schema.at("/properties/reference/maxLength").asInt());
            assertTrue(schema.at("/properties/reference/description").asText().contains("unique per work"));
        }
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
        for (String schema : List.of("ContractBillingControlResponse", "BillingControlSummaryResponse")) {
            var properties = document.path("components").path("schemas").path(schema).path("properties");
            assertCanonicalDecimalProperties(properties, "receivedAmount");
            assertTrue(properties.path("receivedAmount").path("type").toString().contains("null"), schema);
            assertEquals("boolean", properties.at("/receivedAmountAvailable/type").asText());
            assertTrue(properties.at("/receivedAmount/description").asText().contains("RECEIVABLES.READ"));
        }
        assertEquals("date", document.at("/components/schemas/ReceivableBalanceResponse/properties/maxReceivedOn/format").asText());
        assertTrue(document.at("/components/schemas/ReceivableBalanceResponse/properties/maxReceivedOn/description").asText().contains("UTC"));
        assertTrue(document.at("/components/schemas/CreateReceivableSettlementRequest/properties/receivedOn/description").asText().contains("UTC"));
        assertEquals("boolean", document.at("/components/schemas/ReceivableBalanceResponse/properties/settlementCreationAvailable/type").asText());
        assertTrue(document.at("/components/schemas/ReceivableBalanceResponse/properties/settlementCreationBlockReason").toString().contains("SETTLEMENT_INVOICE_LINK_REQUIRED"));
        var blockReason = document.at("/components/schemas/ReceivableBalanceResponse/properties/settlementCreationBlockReason");
        assertTrue(blockReason.path("type").toString().contains("null"));
        assertFalse(blockReason.has("enum"));
        assertEquals("string", document.at("/components/schemas/ProblemDetail/properties/reason/type").asText());
        assertEquals("array", document.at("/components/schemas/ProblemDetail/properties/violations/type").asText());
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
        assertOperation(document, contract + "/closure-eligibility", "get", "200", "400", "401", "403", "404");
        assertOperation(document, contract + "/status", "put", "200", "202", "400", "403", "404", "409");
        assertOperation(document, service + "/status", "put", "200", "202", "400", "403", "404", "409");
        var contractStatusProperties = document.at("/components/schemas/UpdateContractStatusRequest/properties");
        assertEquals(3, contractStatusProperties.size());
        var serviceStatusProperties = document.at("/components/schemas/UpdateContractServiceStatusRequest/properties");
        assertEquals(2, serviceStatusProperties.size());
        for (String prohibited : List.of("companyId", "quantity", "unitPrice", "discountValue", "name")) {
            assertFalse(contractStatusProperties.has(prohibited)); assertFalse(serviceStatusProperties.has(prohibited));
        }
        assertTrue(document.at("/components/schemas/ClosureDecisionResponse/properties/eligible/type").toString().contains("null"));
        assertTrue(document.at("/components/schemas/ClosureDecisionResponse/properties/reason/description").asText().contains("financial blocking reason takes precedence"));
        assertTrue(document.at("/components/schemas/ContractClosureEligibilityResponse/description").asText().contains("CONTRACT_FINANCIAL_RECONCILIATION_REQUIRED"));
        assertTrue(document.at("/components/schemas/ClosureDecisionResponse/properties/authorizationMode/enum").valueStream().anyMatch(tools.jackson.databind.JsonNode::isNull));

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
        assertTrue(document.at("/components/schemas/BillingAllocationControlResponse/properties").has("receivableFinancialStatus"));
        assertTrue(document.at("/components/schemas/BillingAllocationControlResponse/properties/receivableReceivedWithWithholding/type").toString().contains("boolean"));
        assertTrue(document.at("/components/schemas/BillingAllocationControlResponse/required").toString().contains("receivableReceivedWithWithholding"));
        assertTrue(document.at("/components/schemas/BillingAllocationControlResponse/properties/receivableFinancialStatus/enum").valueStream().anyMatch(tools.jackson.databind.JsonNode::isNull));
        assertTrue(document.at("/components/schemas/BillingAllocationControlResponse/required").toString().contains("receivableFinancialStatusAvailable"));
        assertTrue(document.at("/components/schemas/ContractResponse/properties/hasActiveContractDiscount/type").toString().contains("boolean"));
        assertTrue(document.at("/components/schemas/ContractResponse/required").toString().contains("hasActiveContractDiscount"));
        assertTrue(document.at("/components/schemas/UpsertContractServiceRequest/properties").has("sourceServiceTemplateId"));
        assertTrue(document.at("/components/schemas/UpsertContractServiceRequest/properties/discountType/description").asText().contains("preserves the current discount"));
        assertTrue(document.path("paths").path(service).path("put").path("description").asText()
                .contains("SERVICE_DISCOUNT_CHANGE_REQUIRES_DEDICATED_OPERATION"));
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
