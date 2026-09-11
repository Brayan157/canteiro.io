package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyProfileApplicationService {

    private final CompanyRepository companyRepository;
    private final TenantContextHolder tenantContextHolder;
    private final AuditEventRecorder auditEventRecorder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final StorageProvider storageProvider;

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

    @Transactional
    public String uploadLogo(byte[] content, String contentType) {
        accessAuthorizationService.requirePermission(AccessModule.COMPANY, AccessAction.UPDATE_DIRECT);
        Company company = findCurrentCompany();
        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported company logo media type");
        };
        String previousStorageKey = company.getLogo();
        String storageKey = "companies/" + company.getId() + "/logo-" + UUID.randomUUID() + "." + extension;
        storageProvider.store(storageKey, content);
        try {
            Map<String, Object> beforeData = companyAuditData(company);
            company.updateLogo(storageKey);
            Company savedCompany = companyRepository.save(company);
            auditEventRecorder.recordDirectAction(AuditModule.COMPANY, AuditAction.UPDATE, "Company", savedCompany.getId(),
                    beforeData, companyAuditData(savedCompany), Map.of("origin", "logo_upload"));
            if (previousStorageKey != null) {
                storageProvider.delete(previousStorageKey);
            }
            return "/api/v1/company/logo";
        } catch (RuntimeException exception) {
            storageProvider.delete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public CompanyLogoContent loadLogo() {
        Company company = findCurrentCompany();
        if (company.getLogo() == null) {
            throw new IllegalStateException("Company logo was not found");
        }
        return new CompanyLogoContent(storageProvider.load(company.getLogo()), contentType(company.getLogo()));
    }

    private String contentType(String storageKey) {
        if (storageKey.endsWith(".png")) return "image/png";
        if (storageKey.endsWith(".jpg")) return "image/jpeg";
        if (storageKey.endsWith(".webp")) return "image/webp";
        throw new IllegalStateException("Company logo has an unsupported stored media type");
    }

    public record CompanyLogoContent(byte[] content, String contentType) { }

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
