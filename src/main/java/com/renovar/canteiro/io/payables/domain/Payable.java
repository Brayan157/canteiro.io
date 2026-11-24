package com.renovar.canteiro.io.payables.domain;

import lombok.Getter;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Payable {

    private final UUID id;
    private final UUID companyId;
    private final PayableFinancialScope financialScope;
    private final UUID workId;
    private final UUID contractId;
    private final UUID taxObligationId;
    private final String reference;
    private final String description;
    private final LocalDate dueOn;
    private final BigDecimal originalAmount;
    private final PayableStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final UUID financialGroupId;
    private final UUID supplierId;
    private final UUID financialCategoryId;
    private final LocalDate issuedOn;
    private final CurrencyCode currency;
    private final long version;

    private Payable(UUID id, UUID companyId, PayableFinancialScope financialScope, UUID workId, UUID contractId,
                    UUID taxObligationId, String reference, String description, LocalDate dueOn, BigDecimal originalAmount,
                    PayableStatus status, Instant createdAt, Instant updatedAt) {
        this(id,companyId,financialScope,workId,contractId,taxObligationId,reference,description,dueOn,originalAmount,
                status,createdAt,updatedAt,null,null,null,null,CurrencyCode.BRL,0);
    }

    private Payable(UUID id, UUID companyId, PayableFinancialScope financialScope, UUID workId, UUID contractId,
                    UUID taxObligationId, String reference, String description, LocalDate dueOn, BigDecimal originalAmount,
                    PayableStatus status, Instant createdAt, Instant updatedAt, UUID financialGroupId, UUID supplierId,
                    UUID financialCategoryId, LocalDate issuedOn, CurrencyCode currency, long version) {
        this.id = id;
        this.companyId = require(companyId, "Payable company is required");
        this.financialScope = require(financialScope, "Payable financial scope is required");
        validateScope(financialScope,workId,contractId);
        this.workId = workId;
        this.contractId = contractId;
        this.taxObligationId = taxObligationId;
        this.reference = optionalText(reference);
        this.description = optionalText(description);
        this.dueOn = require(dueOn, "Payable due date is required");
        this.originalAmount = positiveAmount(originalAmount);
        this.status = require(status, "Payable status is required");
        if (taxObligationId != null && (financialScope != PayableFinancialScope.WORK || contractId == null)) {
            throw new IllegalArgumentException("Tax payable must have WORK financial scope");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.financialGroupId=financialGroupId;
        this.supplierId=supplierId;
        this.financialCategoryId=financialCategoryId;
        this.issuedOn=issuedOn;
        this.currency=require(currency,"Payable currency is required");
        if(version<0)throw new IllegalArgumentException("Payable version must be non-negative");
        this.version=version;
    }

    public static Payable createTaxPayable(UUID companyId, UUID workId, UUID contractId, UUID taxObligationId,
            String reference, String description, LocalDate dueOn, BigDecimal amount, CurrencyCode currency) {
        return new Payable(null,companyId,PayableFinancialScope.WORK,workId,contractId,require(taxObligationId,"Tax origin is required"),
                reference,description,dueOn,amount,PayableStatus.OPEN,null,null,null,null,null,null,currency,0);
    }

    public static Payable createOperational(UUID companyId, PayableFinancialScope scope, UUID workId, UUID contractId,
            UUID groupId, UUID supplierId, UUID categoryId, CurrencyCode currency, String reference, String description,
            LocalDate issuedOn, LocalDate dueOn, BigDecimal amount) {
        return new Payable(null,companyId,scope,workId,contractId,null,
                PayableCatalogValues.text(reference,100,true),PayableCatalogValues.text(description,1000,false),dueOn,
                exactPositiveAmount(amount),PayableStatus.OPEN,null,null,require(groupId,"Financial group is required"),
                supplierId,categoryId,issuedOn,currency,0);
    }

    public static Payable restore(UUID id, UUID companyId, PayableFinancialScope scope, UUID workId, UUID contractId,
            UUID taxObligationId, String reference, String description, LocalDate dueOn, BigDecimal amount, PayableStatus status,
            Instant createdAt, Instant updatedAt, UUID groupId, UUID supplierId, UUID categoryId, LocalDate issuedOn,
            CurrencyCode currency, long version) {
        return new Payable(id,companyId,scope,workId,contractId,taxObligationId,reference,description,dueOn,amount,status,
                createdAt,updatedAt,groupId,supplierId,categoryId,issuedOn,currency,version);
    }

    public static void validateScope(PayableFinancialScope scope, UUID workId, UUID contractId) {
        require(scope,"Payable financial scope is required");
        if(scope==PayableFinancialScope.WORK && workId==null)throw new IllegalArgumentException("WORK requires workId");
        if(scope==PayableFinancialScope.COMPANY && (workId!=null||contractId!=null))throw new IllegalArgumentException("COMPANY forbids workId and contractId");
    }

    private static BigDecimal exactPositiveAmount(BigDecimal amount) {
        try {
            BigDecimal value=require(amount,"Payable amount is required").setScale(2,RoundingMode.UNNECESSARY);
            if(value.signum()<=0||value.precision()>19)throw new IllegalArgumentException("Amount must be positive with up to 17 integer and two decimal places");
            return value;
        } catch(ArithmeticException e){throw new IllegalArgumentException("Amount must have at most two decimal places");}
    }

    public static Payable createTaxPayable(UUID companyId, UUID workId, UUID contractId, UUID taxObligationId,
                                           String reference, String description, LocalDate dueOn, BigDecimal originalAmount) {
        return new Payable(null, companyId, PayableFinancialScope.WORK, workId, contractId, taxObligationId, reference,
                description, dueOn, originalAmount, PayableStatus.OPEN, null, null);
    }

    public static Payable rehydrate(UUID id, UUID companyId, PayableFinancialScope financialScope, UUID workId,
                                    UUID contractId, UUID taxObligationId, String reference, String description,
                                    LocalDate dueOn, BigDecimal originalAmount, PayableStatus status, Instant createdAt,
                                    Instant updatedAt) {
        return new Payable(id, companyId, financialScope, workId, contractId, taxObligationId, reference, description,
                dueOn, originalAmount, status, createdAt, updatedAt);
    }

    public Payable cancel() {
        if (status == PayableStatus.PAID || status == PayableStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Tax payable must have its payments reversed before cancellation");
        }
        if (status == PayableStatus.CANCELLED) return this;
        return new Payable(id, companyId, financialScope, workId, contractId, taxObligationId, reference, description,
                dueOn, originalAmount, PayableStatus.CANCELLED, createdAt, updatedAt,financialGroupId,supplierId,
                financialCategoryId,issuedOn,currency,version);
    }

    public boolean acceptsSettlements() {
        return status == PayableStatus.OPEN || status == PayableStatus.OVERDUE
                || status == PayableStatus.PARTIALLY_PAID || status == PayableStatus.PAID;
    }

    public Payable withSettlementBalance(BigDecimal paid, LocalDate today) {
        if (!acceptsSettlements() || paid.signum() < 0 || paid.compareTo(originalAmount) > 0) {
            throw new IllegalStateException("Invalid payable settlement balance");
        }
        PayableStatus newStatus = paid.compareTo(originalAmount) == 0 ? PayableStatus.PAID
                : paid.signum() > 0 ? PayableStatus.PARTIALLY_PAID
                : dueOn.isBefore(today) ? PayableStatus.OVERDUE : PayableStatus.OPEN;
        return restore(id, companyId, financialScope, workId, contractId, taxObligationId, reference, description,
                dueOn, originalAmount, newStatus, createdAt, updatedAt, financialGroupId, supplierId,
                financialCategoryId, issuedOn, currency, version);
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        BigDecimal normalized = require(value, "Payable original amount is required").setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Payable original amount must be positive");
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
