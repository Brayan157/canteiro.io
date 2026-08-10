package com.renovar.canteiro.io.dto.model;

import com.renovar.canteiro.io.dto.request.company.CompanyUpdateRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CompanyModel {
    private UUID id;
    private String corporateName;
    private String tradeName;
    private String document;
    private String email;
    private String phone;
    private String address;
    private String logo;
    private Boolean active;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public void update(CompanyUpdateRequest request) {
        if (request.corporateName() != null) {
            this.corporateName = request.corporateName();
        }
        if (request.tradeName() != null) {
            this.tradeName = request.tradeName();
        }
        if (request.document() != null) {
            this.document = request.document();
        }
        if (request.email() != null) {
            this.email = request.email();
        }
        if (request.phone() != null) {
            this.phone = request.phone();
        }
        if (request.address() != null) {
            this.address = request.address();
        }
        if (request.logo() != null) {
            this.logo = request.logo();
        }
    }

    public void deactivate() {
        this.active = false;
    }
    public void activate() {
        this.active = true;
    }
}
