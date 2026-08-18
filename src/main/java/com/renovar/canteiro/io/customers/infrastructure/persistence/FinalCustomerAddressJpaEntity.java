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
@Table(name = "final_customer_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalCustomerAddressJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "final_customer_id", nullable = false, updatable = false)
    private UUID finalCustomerId;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "street", nullable = false, length = 255)
    private String street;

    @Column(name = "number", length = 50)
    private String number;

    @Column(name = "complement", length = 255)
    private String complement;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "primary_address", nullable = false)
    private boolean primaryAddress;

    @Column(name = "active", nullable = false)
    private boolean active;

    public FinalCustomerAddressJpaEntity(UUID companyId, UUID finalCustomerId, String label, String postalCode,
                                         String street, String number, String complement, String district, String city,
                                         String state, String country, boolean primaryAddress, boolean active) {
        this.companyId = companyId;
        this.finalCustomerId = finalCustomerId;
        this.label = label;
        this.postalCode = postalCode;
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.district = district;
        this.city = city;
        this.state = state;
        this.country = country;
        this.primaryAddress = primaryAddress;
        this.active = active;
    }
}
