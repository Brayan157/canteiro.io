package com.renovar.canteiro.io.platform.company.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class Company {

    private final UUID id;
    private String corporateName;
    private String tradeName;
    private String document;
    private String email;
    private String phone;
    private String address;
    private String logo;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Company(
            UUID id,
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.corporateName = corporateName;
        this.tradeName = tradeName;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.logo = logo;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Company create(
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo
    ) {
        return new Company(null, corporateName, tradeName, document, email, phone, address, logo, true, null, null);
    }

    public static Company rehydrate(
            UUID id,
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Company(id, corporateName, tradeName, document, email, phone, address, logo, active, createdAt, updatedAt);
    }

    public void update(
            String corporateName,
            String tradeName,
            String document,
            String email,
            String phone,
            String address,
            String logo
    ) {
        if (corporateName != null) {
            this.corporateName = corporateName;
        }
        if (tradeName != null) {
            this.tradeName = tradeName;
        }
        if (document != null) {
            this.document = document;
        }
        if (email != null) {
            this.email = email;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (address != null) {
            this.address = address;
        }
        if (logo != null) {
            this.logo = logo;
        }
    }

    public void deactivate() {
        active = false;
    }
}
