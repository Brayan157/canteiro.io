package com.renovar.canteiro.io.platform.company.api;

import com.renovar.canteiro.io.platform.company.api.request.CompanyCreateRequest;
import com.renovar.canteiro.io.platform.company.api.request.CompanyUpdateRequest;
import com.renovar.canteiro.io.platform.company.api.response.CompanyResponse;
import com.renovar.canteiro.io.platform.company.application.CreateCompanyCommand;
import com.renovar.canteiro.io.platform.company.application.UpdateCompanyCommand;
import com.renovar.canteiro.io.platform.company.domain.Company;
import org.springframework.stereotype.Component;

@Component
public class CompanyApiMapper {

    public CreateCompanyCommand toCommand(CompanyCreateRequest request) {
        return new CreateCompanyCommand(
                request.corporateName(), request.tradeName(), request.document(), request.email(),
                request.phone(), request.address(), request.logo()
        );
    }


    public UpdateCompanyCommand toCommand(CompanyUpdateRequest request) {
        return new UpdateCompanyCommand(
                request.corporateName(), request.tradeName(), request.document(), request.email(),
                request.phone(), request.address(), request.logo()
        );
    }

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(), company.getCorporateName(), company.getTradeName(), company.getDocument(),
                company.getEmail(), company.getPhone(), company.getAddress(), company.getLogo(), company.isActive(),
                company.getCreatedAt(), company.getUpdatedAt()
        );
    }
}
