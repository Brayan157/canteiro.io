package com.renovar.canteiro.io.platform.subscription.infrastructure.asaas;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayChargeRequest;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayChargeStatus;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayCustomerRequest;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookAuthenticationException;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookEventType;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AsaasSandboxPaymentGatewayTest {

    private static final String BASE_URL = "https://api-sandbox.asaas.com/v3";
    private static final String API_KEY = "$aact_hmlg_test-key";
    private static final String WEBHOOK_TOKEN = "sandbox-webhook-secret";

    private MockRestServiceServer server;
    private AsaasSandboxPaymentGateway gateway;

    @Test
    void exposesItsCodeThroughTheProviderNeutralPort() {
        assertEquals("ASAAS", gateway.providerCode().value());
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("access_token", API_KEY)
                .defaultHeader("User-Agent", "canteiro.io/test (sandbox)");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new AsaasSandboxPaymentGateway(
                builder.build(),
                JsonMapper.builder().build(),
                properties()
        );
    }

    @Test
    void createsSandboxCustomerWithoutExposingAsaasContractsToTheApplicationPort() {
        UUID companyId = UUID.randomUUID();
        server.expect(once(), requestTo(BASE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", API_KEY))
                .andExpect(header("User-Agent", "canteiro.io/test (sandbox)"))
                .andExpect(content().json("""
                        {
                          "name":"Construtora Sandbox",
                          "cpfCnpj":"12345678000199",
                          "email":"billing@example.com",
                          "mobilePhone":"11999999999",
                          "externalReference":"%s",
                          "notificationDisabled":true
                        }
                        """.formatted(companyId)))
                .andRespond(withSuccess("{\"id\":\"cus_sandbox_123\"}", MediaType.APPLICATION_JSON));

        var result = gateway.createCustomer(new PaymentGatewayCustomerRequest(
                companyId, "Construtora Sandbox", "12345678000199",
                "billing@example.com", "11999999999"
        ));

        assertEquals("cus_sandbox_123", result.externalCustomerId());
        server.verify();
    }

    @Test
    void createsPixChargeAndMapsTheAsaasStatus() {
        UUID subscriptionId = UUID.randomUUID();
        server.expect(once(), requestTo(BASE_URL + "/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "customer":"cus_sandbox_123",
                          "billingType":"PIX",
                          "value":149.90,
                          "dueDate":"2026-09-11",
                          "description":"Canteiro.io subscription %s",
                          "externalReference":"charge-idempotency-key"
                        }
                        """.formatted(subscriptionId)))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_sandbox_123\",\"status\":\"PENDING\"}",
                        MediaType.APPLICATION_JSON
                ));

        var result = gateway.createCharge(new PaymentGatewayChargeRequest(
                subscriptionId,
                "cus_sandbox_123",
                new BigDecimal("149.90"),
                LocalDate.of(2026, 9, 11),
                PaymentGatewayBillingMethod.PIX,
                "charge-idempotency-key"
        ));

        assertEquals("pay_sandbox_123", result.externalChargeId());
        assertEquals(PaymentGatewayChargeStatus.PENDING, result.status());
        server.verify();
    }

    @Test
    void authenticatesAndTranslatesAConfirmedPaymentWebhook() {
        String payload = """
                {
                  "id":"evt_sandbox_123",
                  "event":"PAYMENT_RECEIVED",
                  "dateCreated":"2026-08-12 16:45:03",
                  "payment":{
                    "id":"pay_sandbox_123",
                    "status":"RECEIVED",
                    "billingType":"PIX",
                    "externalReference":"charge-idempotency-key",
                    "futureField":"ignored"
                  },
                  "futureTopLevelField":"ignored"
                }
                """;

        var webhook = gateway.verifyAndParseWebhook(new PaymentGatewayWebhookRequest(
                payload.getBytes(StandardCharsets.UTF_8),
                Map.of("asaas-access-token", WEBHOOK_TOKEN),
                Instant.parse("2026-08-12T20:00:00Z")
        ));

        assertEquals("evt_sandbox_123", webhook.externalEventId());
        assertEquals("pay_sandbox_123", webhook.externalChargeId());
        assertEquals(PaymentGatewayWebhookEventType.CHARGE_CONFIRMED, webhook.eventType());
        assertEquals(Instant.parse("2026-08-12T19:45:03Z"), webhook.occurredAt());
        assertEquals("charge-idempotency-key", webhook.attributes().get("externalReference"));
    }

    @Test
    void rejectsWebhookBeforeParsingWhenItsTokenIsInvalid() {
        assertThrows(PaymentGatewayWebhookAuthenticationException.class, () -> gateway.verifyAndParseWebhook(
                new PaymentGatewayWebhookRequest(
                        "{}".getBytes(StandardCharsets.UTF_8),
                        Map.of("asaas-access-token", "invalid-token"),
                        Instant.now()
                )
        ));
    }

    private AsaasSandboxProperties properties() {
        return new AsaasSandboxProperties(
                URI.create(BASE_URL),
                API_KEY,
                WEBHOOK_TOKEN,
                "canteiro.io/test (sandbox)",
                ZoneId.of("America/Sao_Paulo")
        );
    }
}
