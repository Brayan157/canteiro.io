package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "contract")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "work_id", nullable = false, updatable = false)
    private UUID workId;

    @Column(name = "contract_identifier", length = 100)
    private String contractIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 30)
    private ContractPricingMode pricingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "expected_completion_on")
    private LocalDate expectedCompletionOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Version
    @Column(name = "billing_version", nullable = false)
    private Long billingVersion;

    public ContractJpaEntity(UUID companyId, UUID workId, String reference, String name, ContractStatus status,
                             LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        this(companyId, workId, null, ContractPricingMode.FIXED, reference, name, status, startedOn, expectedCompletionOn, completedOn);
    }

    public ContractJpaEntity(UUID companyId, UUID workId, String contractIdentifier, String reference, String name, ContractStatus status,
                             LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        this(companyId, workId, contractIdentifier, ContractPricingMode.FIXED, reference, name, status, startedOn, expectedCompletionOn, completedOn);
    }

    public ContractJpaEntity(UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode, String reference, String name, ContractStatus status,
                             LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        this(companyId, workId, contractIdentifier, pricingMode, reference, name, status, startedOn,
                expectedCompletionOn, completedOn, CurrencyCode.BRL);
    }

    public ContractJpaEntity(UUID companyId, UUID workId, String contractIdentifier, ContractPricingMode pricingMode, String reference, String name, ContractStatus status,
                             LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn, CurrencyCode currency) {
        this.companyId = companyId;
        this.workId = workId;
        this.contractIdentifier = contractIdentifier;
        this.pricingMode = pricingMode == null ? ContractPricingMode.FIXED : pricingMode;
        this.currency = CurrencyCode.defaultIfAbsent(currency);
        this.reference = reference;
        this.name = name;
        this.status = status;
        this.startedOn = startedOn;
        this.expectedCompletionOn = expectedCompletionOn;
        this.completedOn = completedOn;
    }

    public void updateProfile(String reference, String name, ContractStatus status, LocalDate startedOn,
                              LocalDate expectedCompletionOn, LocalDate completedOn) {
        updateProfile(contractIdentifier, reference, name, status, startedOn, expectedCompletionOn, completedOn);
    }

    public void updateProfile(String contractIdentifier, String reference, String name, ContractStatus status, LocalDate startedOn,
                              LocalDate expectedCompletionOn, LocalDate completedOn) {
        this.contractIdentifier = contractIdentifier;
        this.reference = reference;
        this.name = name;
        this.status = status;
        this.startedOn = startedOn;
        this.expectedCompletionOn = expectedCompletionOn;
        this.completedOn = completedOn;
    }
}
