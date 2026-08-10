package com.renovar.canteiro.io.service.contract;

import com.renovar.canteiro.io.dto.request.company.CompanyCreateRequest;
import com.renovar.canteiro.io.dto.request.company.CompanyUpdateRequest;
import com.renovar.canteiro.io.dto.response.company.CompanyResponse;

import java.util.List;
import java.util.UUID;

public interface CompanyService {
    CompanyResponse createCompany(CompanyCreateRequest request);
    CompanyResponse findById(UUID id);
    List<CompanyResponse> findAll();
    CompanyResponse update(UUID id, CompanyUpdateRequest request);
    void delete(UUID id);
}