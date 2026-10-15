package com.renovar.canteiro.io.contracts.domain;

import lombok.Getter;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Contract {

    private final UUID id;
    private final UUID companyId;
    private final UUID workId;
    private final String contractIdentifier;
    private final ContractPricingMode pricingMode;
    private final CurrencyCode currency;
    private final String reference;
    private final String name;
    private final ContractStatus status;
    private final LocalDate startedOn;
    private final LocalDate expectedCompletionOn;
    private final LocalDate completedOn;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Contract(UUID id, UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode, String reference, String name, ContractStatus status,
                     LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn, Instant createdAt,
                     Instant updatedAt, CurrencyCode currency) {
        this.id = id;
        this.companyId = require(companyId, "Contract company is required");
        this.workId = require(workId, "Contract work is required");
        this.contractIdentifier = normalize(contractIdentifier);
        this.pricingMode = pricingMode == null ? ContractPricingMode.FIXED : pricingMode;
        this.currency = CurrencyCode.defaultIfAbsent(currency);
        this.reference = normalize(reference);
        this.name = requireName(name);
        this.status = require(status, "Contract status is required");
        validateDates(startedOn, expectedCompletionOn, completedOn);
        this.startedOn = startedOn;
        this.expectedCompletionOn = expectedCompletionOn;
        this.completedOn = completedOn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Contract create(UUID companyId, UUID workId, String reference, String name, ContractStatus status,
                                  LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        return create(companyId, workId, null, ContractPricingMode.FIXED, reference, name, status, startedOn, expectedCompletionOn, completedOn);
    }

    public static Contract create(UUID companyId, UUID workId, String contractIdentifier, String reference, String name, ContractStatus status,
                                  LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        return create(companyId, workId, contractIdentifier, ContractPricingMode.FIXED, reference, name, status, startedOn, expectedCompletionOn, completedOn);
    }

    public static Contract create(UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode,
                                  String reference, String name, ContractStatus status, LocalDate startedOn,
                                  LocalDate expectedCompletionOn, LocalDate completedOn) {
        return create(companyId, workId, contractIdentifier, pricingMode, reference, name, status, startedOn,
                expectedCompletionOn, completedOn, CurrencyCode.BRL);
    }

    public static Contract create(UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode,
                                  String reference, String name, ContractStatus status, LocalDate startedOn,
                                  LocalDate expectedCompletionOn, LocalDate completedOn, CurrencyCode currency) {
        return new Contract(null, companyId, workId, contractIdentifier, pricingMode, reference, name, status, startedOn, expectedCompletionOn,
                completedOn, null, null, currency);
    }

    public static Contract rehydrate(UUID id, UUID companyId, UUID workId, String reference, String name,
                                     ContractStatus status, LocalDate startedOn, LocalDate expectedCompletionOn,
                                     LocalDate completedOn, Instant createdAt, Instant updatedAt) {
        return rehydrate(id, companyId, workId, null, ContractPricingMode.FIXED, reference, name, status, startedOn, expectedCompletionOn, completedOn, createdAt, updatedAt);
    }

    public static Contract rehydrate(UUID id, UUID companyId, UUID workId, String contractIdentifier, String reference, String name,
                                     ContractStatus status, LocalDate startedOn, LocalDate expectedCompletionOn,
                                     LocalDate completedOn, Instant createdAt, Instant updatedAt) {
        return rehydrate(id, companyId, workId, contractIdentifier, ContractPricingMode.FIXED, reference, name, status, startedOn, expectedCompletionOn, completedOn, createdAt, updatedAt);
    }

    public static Contract rehydrate(UUID id, UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode,
                                     String reference, String name, ContractStatus status, LocalDate startedOn, LocalDate expectedCompletionOn,
                                     LocalDate completedOn, Instant createdAt, Instant updatedAt) {
        return rehydrate(id, companyId, workId, contractIdentifier, pricingMode, reference, name, status, startedOn,
                expectedCompletionOn, completedOn, createdAt, updatedAt, CurrencyCode.BRL);
    }

    public static Contract rehydrate(UUID id, UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode,
                                     String reference, String name, ContractStatus status, LocalDate startedOn, LocalDate expectedCompletionOn,
                                     LocalDate completedOn, Instant createdAt, Instant updatedAt, CurrencyCode currency) {
        return new Contract(id, companyId, workId, contractIdentifier, pricingMode, reference, name, status, startedOn, expectedCompletionOn,
                completedOn, createdAt, updatedAt, currency);
    }

    public Contract updateProfile(String reference, String name, ContractStatus status, LocalDate startedOn,
                                  LocalDate expectedCompletionOn, LocalDate completedOn) {
        return updateProfile(contractIdentifier, reference, name, status, startedOn, expectedCompletionOn, completedOn);
    }

    public Contract updateProfile(String contractIdentifier, String reference, String name, ContractStatus status, LocalDate startedOn,
                                  LocalDate expectedCompletionOn, LocalDate completedOn) {
        return new Contract(id, companyId, workId, contractIdentifier, pricingMode, reference, name, status, startedOn, expectedCompletionOn,
                completedOn, createdAt, updatedAt, currency);
    }

    private static void validateDates(LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        if (startedOn != null && expectedCompletionOn != null && expectedCompletionOn.isBefore(startedOn)) {
            throw new IllegalArgumentException("Contract expected completion must not precede its start date");
        }
        if (startedOn != null && completedOn != null && completedOn.isBefore(startedOn)) {
            throw new IllegalArgumentException("Contract completion must not precede its start date");
        }
    }

    private static String requireName(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Contract name is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
