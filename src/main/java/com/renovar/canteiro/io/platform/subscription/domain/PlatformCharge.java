package com.renovar.canteiro.io.platform.subscription.domain;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class PlatformCharge {

    private final UUID id;
    private final UUID companyId;
    private final UUID subscriptionId;
    private final PaymentGatewayProviderCode provider;
    private final String idempotencyKey;
    private final String externalCustomerId;
    private final String externalChargeId;
    private final PaymentGatewayBillingMethod billingMethod;
    private final BigDecimal amount;
    private final LocalDate dueDate;
    private PlatformChargeStatus status;
    private Instant lastGatewayEventAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlatformCharge(
            UUID id,
            UUID companyId,
            UUID subscriptionId,
            PaymentGatewayProviderCode provider,
            String idempotencyKey,
            String externalCustomerId,
            String externalChargeId,
            PaymentGatewayBillingMethod billingMethod,
            BigDecimal amount,
            LocalDate dueDate,
            PlatformChargeStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant lastGatewayEventAt
    ) {
        this.id = id;
        this.companyId = requireId(companyId, "Platform charge company is required");
        this.subscriptionId = requireId(subscriptionId, "Platform charge subscription is required");
        this.provider = require(provider, "Platform charge provider is required");
        this.idempotencyKey = requireText(idempotencyKey, "Platform charge idempotency key is required");
        this.externalCustomerId = requireText(externalCustomerId, "Platform charge external customer is required");
        this.externalChargeId = requireText(externalChargeId, "Platform charge external id is required");
        this.billingMethod = require(billingMethod, "Platform charge billing method is required");
        this.amount = requireAmount(amount);
        this.dueDate = require(dueDate, "Platform charge due date is required");
        this.status = require(status, "Platform charge status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastGatewayEventAt = lastGatewayEventAt;
    }

    public static PlatformCharge create(
            UUID companyId,
            UUID subscriptionId,
            PaymentGatewayProviderCode provider,
            String idempotencyKey,
            String externalCustomerId,
            String externalChargeId,
            PaymentGatewayBillingMethod billingMethod,
            BigDecimal amount,
            LocalDate dueDate,
            PlatformChargeStatus status
    ) {
        return new PlatformCharge(
                null, companyId, subscriptionId, provider, idempotencyKey, externalCustomerId,
                externalChargeId, billingMethod, amount, dueDate, status, null, null, null
        );
    }

    public static PlatformCharge rehydrate(
            UUID id,
            UUID companyId,
            UUID subscriptionId,
            PaymentGatewayProviderCode provider,
            String idempotencyKey,
            String externalCustomerId,
            String externalChargeId,
            PaymentGatewayBillingMethod billingMethod,
            BigDecimal amount,
            LocalDate dueDate,
            PlatformChargeStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant lastGatewayEventAt
    ) {
        return new PlatformCharge(
                id, companyId, subscriptionId, provider, idempotencyKey, externalCustomerId,
                externalChargeId, billingMethod, amount, dueDate, status, createdAt, updatedAt, lastGatewayEventAt
        );
    }

    public static PlatformCharge rehydrate(
            UUID id,
            UUID companyId,
            UUID subscriptionId,
            PaymentGatewayProviderCode provider,
            String idempotencyKey,
            String externalCustomerId,
            String externalChargeId,
            PaymentGatewayBillingMethod billingMethod,
            BigDecimal amount,
            LocalDate dueDate,
            PlatformChargeStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return rehydrate(
                id, companyId, subscriptionId, provider, idempotencyKey, externalCustomerId,
                externalChargeId, billingMethod, amount, dueDate, status, createdAt, updatedAt, null
        );
    }

    public boolean applyGatewayStatus(PlatformChargeStatus newStatus, Instant observedAt) {
        require(newStatus, "Platform charge status is required");
        require(observedAt, "Gateway observation time is required");
        if (lastGatewayEventAt != null && !observedAt.isAfter(lastGatewayEventAt)) {
            return false;
        }
        boolean changed = status != newStatus;
        status = newStatus;
        lastGatewayEventAt = observedAt;
        return changed;
    }

    public boolean matches(
            UUID requestedSubscriptionId,
            String requestedExternalCustomerId,
            PaymentGatewayBillingMethod requestedBillingMethod,
            BigDecimal requestedAmount,
            LocalDate requestedDueDate
    ) {
        return subscriptionId.equals(requestedSubscriptionId)
                && externalCustomerId.equals(requestedExternalCustomerId)
                && billingMethod == requestedBillingMethod
                && amount.compareTo(requestedAmount) == 0
                && dueDate.equals(requestedDueDate);
    }

    public boolean isUnpaidOn(LocalDate currentDate) {
        if (currentDate == null) {
            throw new IllegalArgumentException("Platform charge assessment date is required");
        }
        return (status == PlatformChargeStatus.PENDING || status == PlatformChargeStatus.OVERDUE)
                && !currentDate.isBefore(dueDate);
    }

    public boolean isOverdueOn(LocalDate currentDate) {
        return isUnpaidOn(currentDate) && currentDate.isAfter(dueDate);
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Platform charge amount must be greater than or equal to zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Platform charge amount must have at most two decimal places", exception);
        }
    }

    private static UUID requireId(UUID value, String message) {
        return require(value, message);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
