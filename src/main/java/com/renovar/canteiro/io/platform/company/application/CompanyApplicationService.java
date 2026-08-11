package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyApplicationService {

    private final CompanyRepository companyRepository;

    @Transactional
    public Company create(CreateCompanyCommand command) {
        Company company = Company.create(
                command.corporateName(),
                command.tradeName(),
                command.document(),
                command.email(),
                command.phone(),
                command.address(),
                command.logo()
        );
        return companyRepository.save(company);
    }

    @Transactional(readOnly = true)
    public Optional<Company> findById(UUID id) {
        return companyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Transactional
    public Optional<Company> update(UUID id, UpdateCompanyCommand command) {
        return companyRepository.findById(id)
                .map(company -> {
                    company.update(
                            command.corporateName(),
                            command.tradeName(),
                            command.document(),
                            command.email(),
                            command.phone(),
                            command.address(),
                            command.logo()
                    );
                    return companyRepository.save(company);
                });
    }

    @Transactional
    public void deactivate(UUID id) {
        companyRepository.findById(id).ifPresent(company -> {
            company.deactivate();
            companyRepository.save(company);
        });
    }
}
