package com.renovar.canteiro.io.service.implementation;

import com.renovar.canteiro.io.dto.mapper.CompanyMapper;
import com.renovar.canteiro.io.dto.model.CompanyModel;
import com.renovar.canteiro.io.dto.request.company.CompanyCreateRequest;
import com.renovar.canteiro.io.dto.request.company.CompanyUpdateRequest;
import com.renovar.canteiro.io.dto.response.company.CompanyResponse;
import com.renovar.canteiro.io.repository.contract.CompanyRepository;
import com.renovar.canteiro.io.service.contract.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    @Override
    public CompanyResponse createCompany(CompanyCreateRequest request) {
        CompanyModel model = companyMapper.toModel(request);
        CompanyModel company = companyRepository.save(model);
        return companyMapper.toResponse(company);
    }

    @Override
    public CompanyResponse findById(UUID id) {
        CompanyModel company = companyRepository.findById(id);
        return company != null ? companyMapper.toResponse(company) : null;
    }

    @Override
    public List<CompanyResponse> findAll() {
        return companyRepository.findAll().stream()
                .map(companyMapper::toResponse)
                .toList();
    }

    @Override
    public CompanyResponse update(UUID id, CompanyUpdateRequest request) {
        CompanyModel model = companyRepository.findById(id);
        if (model == null) {
            return null;
        }
        model.update(request);
        CompanyModel updatedCompany = companyRepository.save(model);
        return companyMapper.toResponse(updatedCompany);
    }

    @Override
    public void delete(UUID id) {
        CompanyModel model = companyRepository.findById(id);
        if (model != null) {
            model.deactivate();
            companyRepository.save(model);
        }
    }
}
