package com.renovar.canteiro.io.dto.mapper;

import com.renovar.canteiro.io.dto.model.CompanyModel;
import com.renovar.canteiro.io.dto.request.company.CompanyCreateRequest;
import com.renovar.canteiro.io.dto.response.company.CompanyResponse;
import com.renovar.canteiro.io.entity.Company;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {
    public CompanyModel toModel(CompanyCreateRequest request) {
        return CompanyModel.builder()
                .corporateName(request.corporateName())
                .tradeName(request.tradeName())
                .document(request.document())
                .email(request.email())
                .phone(request.phone())
                .address(request.address())
                .logo(request.logo())
                .active(true)
                .build();
    }
    public CompanyModel toModel(Company entity) {
        return CompanyModel.builder()
                .id(entity.getId())
                .corporateName(entity.getCorporateName())
                .tradeName(entity.getTradeName())
                .document(entity.getDocument())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .logo(entity.getLogo())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
    public Company toEntity(CompanyModel model) {
        return Company.builder()
                .corporateName(model.getCorporateName())
                .tradeName(model.getTradeName())
                .document(model.getDocument())
                .email(model.getEmail())
                .phone(model.getPhone())
                .address(model.getAddress())
                .logo(model.getLogo())
                .active(model.getActive())
                .build();
    }
    public CompanyResponse toResponse(CompanyModel model) {
        return new CompanyResponse(
                model.getId(),
                model.getCorporateName(),
                model.getTradeName(),
                model.getDocument(),
                model.getEmail(),
                model.getPhone(),
                model.getAddress(),
                model.getLogo(),
                model.getActive(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}