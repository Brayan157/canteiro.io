package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "final_customer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_final_customer_company_document",
                columnNames = {"company_id", "document"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalCustomerJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, updatable = false, length = 20)
    private FinalCustomerType customerType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "document", nullable = false, updatable = false, length = 20)
    private String document;

    @Column(name = "active", nullable = false)
    private boolean active;

    public FinalCustomerJpaEntity(
            UUID companyId,
            FinalCustomerType customerType,
            String name,
            String document,
            boolean active
    ) {
        this.companyId = companyId;
        this.customerType = customerType;
        this.name = name;
        this.document = document;
        this.active = active;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }
}
