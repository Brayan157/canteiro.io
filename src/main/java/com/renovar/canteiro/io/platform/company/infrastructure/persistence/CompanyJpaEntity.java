package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "company")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyJpaEntity extends BaseJpaEntity {

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

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "logo")
    private String logo;

    public CompanyJpaEntity(
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo,
            boolean active
    ) {
        this.corporateName = corporateName;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.logo = logo;
        this.active = active;
    }

    public void update(
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo,
            boolean active
    ) {
        this.corporateName = corporateName;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.logo = logo;
        this.active = active;
    }
}
