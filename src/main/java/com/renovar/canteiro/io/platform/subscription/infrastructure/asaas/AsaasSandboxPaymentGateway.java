package com.renovar.canteiro.io.platform.subscription.infrastructure.asaas;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGateway;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayChargeRequest;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayChargeResult;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayChargeStatus;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayCustomerRequest;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayCustomerResult;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayException;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhook;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookAuthenticationException;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookEventType;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookRequest;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AsaasSandboxPaymentGateway implements PaymentGateway {

    private static final DateTimeFormatter ASAAS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final PaymentGatewayProviderCode PROVIDER_CODE = new PaymentGatewayProviderCode("ASAAS");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AsaasSandboxProperties properties;

    public AsaasSandboxPaymentGateway(
            RestClient restClient,
            ObjectMapper objectMapper,
            AsaasSandboxProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public PaymentGatewayProviderCode providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public PaymentGatewayCustomerResult createCustomer(PaymentGatewayCustomerRequest request) {
        AsaasCustomerResponse response = execute(() -> restClient.post()
                .uri("/customers")
                .body(new AsaasCustomerRequest(
                        request.name(),
                        request.document(),
                        request.email(),
                        request.phone(),
                        request.companyId().toString(),
                        true
                ))
                .retrieve()
                .body(AsaasCustomerResponse.class));
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new PaymentGatewayException("Asaas sandbox returned a customer without an id");
        }
        return new PaymentGatewayCustomerResult(response.id());
    }

    @Override
    public PaymentGatewayChargeResult createCharge(PaymentGatewayChargeRequest request) {
        AsaasChargeResponse response = execute(() -> restClient.post()
                .uri("/payments")
                .body(new AsaasChargeRequest(
                        request.externalCustomerId(),
                        toAsaasBillingType(request.billingMethod()),
                        request.amount(),
                        request.dueDate(),
                        "Canteiro.io subscription " + request.subscriptionId(),
                        request.idempotencyKey()
                ))
                .retrieve()
                .body(AsaasChargeResponse.class));
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new PaymentGatewayException("Asaas sandbox returned a charge without an id");
        }
        return new PaymentGatewayChargeResult(response.id(), toGatewayChargeStatus(response.status()));
    }

    @Override
    public PaymentGatewayChargeStatus findChargeStatus(String externalChargeId) {
        if (externalChargeId == null || externalChargeId.isBlank()) {
            throw new IllegalArgumentException("External charge id is required");
        }
        AsaasChargeResponse response = execute(() -> restClient.get()
                .uri("/payments/{id}", externalChargeId.trim())
                .retrieve()
                .body(AsaasChargeResponse.class));
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new PaymentGatewayException("Asaas sandbox returned a charge without an id");
        }
        return toGatewayChargeStatus(response.status());
    }

    @Override
    public PaymentGatewayWebhook verifyAndParseWebhook(PaymentGatewayWebhookRequest request) {
        authenticateWebhook(request.headers().get("asaas-access-token"));
        try {
            AsaasWebhookPayload payload = objectMapper.readValue(request.rawPayload(), AsaasWebhookPayload.class);
            if (payload.payment() == null || payload.payment().id() == null) {
                throw new PaymentGatewayException("Asaas webhook does not identify a payment");
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            putIfPresent(attributes, "status", payload.payment().status());
            putIfPresent(attributes, "billingType", payload.payment().billingType());
            putIfPresent(attributes, "externalReference", payload.payment().externalReference());
            return new PaymentGatewayWebhook(
                    payload.id(),
                    payload.payment().id(),
                    toGatewayWebhookEventType(payload.event()),
                    LocalDateTime.parse(payload.dateCreated(), ASAAS_DATE_TIME)
                            .atZone(properties.webhookZone())
                            .toInstant(),
                    attributes
            );
        } catch (PaymentGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentGatewayException("Asaas webhook payload is invalid", exception);
        }
    }

    private void authenticateWebhook(String receivedToken) {
        if (receivedToken == null || !MessageDigest.isEqual(
                properties.webhookToken().getBytes(StandardCharsets.UTF_8),
                receivedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new PaymentGatewayWebhookAuthenticationException("Asaas webhook authentication failed");
        }
    }

    private PaymentGatewayWebhookEventType toGatewayWebhookEventType(String event) {
        if (event == null) {
            throw new PaymentGatewayException("Asaas webhook event type is required");
        }
        return switch (event) {
            case "PAYMENT_CREATED" -> PaymentGatewayWebhookEventType.CHARGE_CREATED;
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> PaymentGatewayWebhookEventType.CHARGE_CONFIRMED;
            case "PAYMENT_OVERDUE" -> PaymentGatewayWebhookEventType.CHARGE_OVERDUE;
            case "PAYMENT_DELETED", "PAYMENT_REFUNDED" -> PaymentGatewayWebhookEventType.CHARGE_CANCELLED;
            default -> throw new PaymentGatewayException("Unsupported Asaas webhook event: " + event);
        };
    }

    private PaymentGatewayChargeStatus toGatewayChargeStatus(String status) {
        if (status == null) {
            throw new PaymentGatewayException("Asaas charge status is required");
        }
        return switch (status) {
            case "CONFIRMED", "RECEIVED" -> PaymentGatewayChargeStatus.CONFIRMED;
            case "OVERDUE" -> PaymentGatewayChargeStatus.OVERDUE;
            case "REFUNDED", "REFUND_REQUESTED", "DELETED" -> PaymentGatewayChargeStatus.CANCELLED;
            default -> PaymentGatewayChargeStatus.PENDING;
        };
    }

    private String toAsaasBillingType(PaymentGatewayBillingMethod billingMethod) {
        return switch (billingMethod) {
            case CREDIT_CARD -> "CREDIT_CARD";
            case PIX -> "PIX";
            case BANK_SLIP -> "BOLETO";
        };
    }

    private <T> T execute(GatewayCall<T> call) {
        try {
            return call.execute();
        } catch (RestClientResponseException exception) {
            throw new PaymentGatewayException(
                    "Asaas sandbox request failed with HTTP " + exception.getStatusCode().value(), exception
            );
        } catch (RestClientException exception) {
            throw new PaymentGatewayException("Asaas sandbox request failed", exception);
        }
    }

    private void putIfPresent(Map<String, String> attributes, String name, String value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }

    @FunctionalInterface
    private interface GatewayCall<T> {
        T execute();
    }

    private record AsaasCustomerRequest(
            String name,
            String cpfCnpj,
            String email,
            String mobilePhone,
            String externalReference,
            boolean notificationDisabled
    ) {
    }

    private record AsaasCustomerResponse(String id) {
    }

    private record AsaasChargeRequest(
            String customer,
            String billingType,
            java.math.BigDecimal value,
            LocalDate dueDate,
            String description,
            String externalReference
    ) {
    }

    private record AsaasChargeResponse(String id, String status) {
    }

    private record AsaasWebhookPayload(
            String id,
            String event,
            String dateCreated,
            AsaasWebhookPayment payment
    ) {
    }

    private record AsaasWebhookPayment(
            String id,
            String status,
            String billingType,
            String externalReference
    ) {
    }
}
