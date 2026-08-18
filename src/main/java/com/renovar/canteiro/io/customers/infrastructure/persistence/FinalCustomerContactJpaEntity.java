package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "final_customer_contact")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalCustomerContactJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "final_customer_id", nullable = false, updatable = false)
    private UUID finalCustomerId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "active", nullable = false)
    private boolean active;

    public FinalCustomerContactJpaEntity(UUID companyId, UUID finalCustomerId, String name, String email, String phone,
                                         boolean primaryContact, boolean active) {
        this.companyId = companyId;
        this.finalCustomerId = finalCustomerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.primaryContact = primaryContact;
        this.active = active;
    }
}
