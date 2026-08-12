package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyProfileApplicationService {

    private final CompanyRepository companyRepository;
    private final TenantContextHolder tenantContextHolder;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional(readOnly = true)
    public Company findCurrentCompany() {
        return companyRepository.findById(tenantContextHolder.requireCurrentTenant().companyId())
                .orElseThrow(() -> new IllegalStateException("Current tenant company does not exist"));
    }

    @Transactional
    public Company updateCurrentCompany(UpdateCompanyCommand command) {
        Company company = findCurrentCompany();
        Map<String, Object> beforeData = companyAuditData(company);
        company.update(
                command.corporateName(),
                command.tradeName(),
                command.document(),
                command.email(),
                command.phone(),
                command.address(),
                command.logo()
        );
        Company updatedCompany = companyRepository.save(company);
        auditEventRecorder.recordDirectAction(
                AuditModule.COMPANY,
                AuditAction.UPDATE,
                "Company",
                updatedCompany.getId(),
                beforeData,
                companyAuditData(updatedCompany),
                Map.of()
        );
        return updatedCompany;
    }

    private Map<String, Object> companyAuditData(Company company) {
        Map<String, Object> companyData = new HashMap<>();
        companyData.put("corporateName", company.getCorporateName());
        companyData.put("tradeName", company.getTradeName());
        companyData.put("document", company.getDocument());
        companyData.put("email", company.getEmail());
        companyData.put("phone", company.getPhone());
        companyData.put("address", company.getAddress());
        companyData.put("logo", company.getLogo());
        companyData.put("active", company.isActive());
        return companyData;
    }
}
