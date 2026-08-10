package com.renovar.canteiro.io.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "company")
public class Company extends BaseEntity {
    @Column(name = "corporate_name", nullable = false)
    private String corporateName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "document", nullable = false, unique = true, length = 20)
    private String document;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "logo")
    private String logo;

    @Builder
    public Company(
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo,
            Boolean active
    ) {
        this.corporateName = corporateName;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.logo = logo;
        this.active = active != null ? active : true;
    }
    public void activate() {
        this.active = true;
    }
    public void deactivate() {
        this.active = false;
    }
    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }
    public void updateBasicInformation(
            String corporateName,
            String tradeName,
            String document,
            String address,
            String logo
    ) {
        this.corporateName = corporateName;
        this.tradeName = tradeName;
        this.document = document;
        this.address = address;
        this.logo = logo;
    }
}
