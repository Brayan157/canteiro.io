package com.renovar.canteiro.io.receivables.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Invoice {

    private final UUID id;
    private final UUID companyId;
    private final UUID contractId;
    private final String number;
    private final String series;
    private final String accessKey;
    private final LocalDate issuedOn;
    private final BigDecimal totalAmount;
    private final InvoiceStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Invoice(UUID id, UUID companyId, UUID contractId, String number, String series, String accessKey,
                    LocalDate issuedOn, BigDecimal totalAmount, InvoiceStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Invoice company is required");
        this.contractId = require(contractId, "Invoice contract is required");
        this.number = requiredText(number, "Invoice number is required");
        this.series = optionalText(series);
        this.accessKey = optionalText(accessKey);
        this.issuedOn = require(issuedOn, "Invoice issue date is required");
        this.totalAmount = positiveAmount(totalAmount);
        this.status = require(status, "Invoice status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Invoice register(UUID companyId, UUID contractId, String number, String series, String accessKey,
                                   LocalDate issuedOn, BigDecimal totalAmount) {
        return new Invoice(null, companyId, contractId, number, series, accessKey, issuedOn, totalAmount,
                InvoiceStatus.REGISTERED, null, null);
    }

    public static Invoice rehydrate(UUID id, UUID companyId, UUID contractId, String number, String series,
                                    String accessKey, LocalDate issuedOn, BigDecimal totalAmount, InvoiceStatus status,
                                    Instant createdAt, Instant updatedAt) {
        return new Invoice(id, companyId, contractId, number, series, accessKey, issuedOn, totalAmount, status,
                createdAt, updatedAt);
    }

    public Invoice cancel() {
        if (status == InvoiceStatus.CANCELLED) throw new IllegalStateException("Invoice is already cancelled");
        return new Invoice(id, companyId, contractId, number, series, accessKey, issuedOn, totalAmount,
                InvoiceStatus.CANCELLED, createdAt, updatedAt);
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Invoice total amount is required").setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Invoice total amount must be positive");
        }
        return normalized;
    }

    private static String requiredText(String value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
