package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Loads tenant branding only for private, server-rendered measurement documents. */
@Component
@RequiredArgsConstructor
public class MeasurementCompanyBrandingLoader {

    private static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;

    private final CompanyRepository companyRepository;
    private final StorageProvider storageProvider;

    public MeasurementCompanyBranding load(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Current tenant company does not exist"));
        String displayName = company.getTradeName() == null || company.getTradeName().isBlank()
                ? company.getCorporateName()
                : company.getTradeName();
        String logoStorageKey = company.getLogo();
        if (!isCurrentCompanyLogoStorageKey(company, logoStorageKey)) {
            return new MeasurementCompanyBranding(displayName, company.getDocument(), null);
        }
        try {
            byte[] logoContent = storageProvider.load(logoStorageKey);
            return new MeasurementCompanyBranding(displayName, company.getDocument(),
                    logoContent.length <= MAX_LOGO_BYTES ? logoContent : null);
        } catch (RuntimeException ignored) {
            // An unavailable private logo must not prevent an official document export.
            return new MeasurementCompanyBranding(displayName, company.getDocument(), null);
        }
    }

    private boolean isCurrentCompanyLogoStorageKey(Company company, String storageKey) {
        if (storageKey == null || company.getId() == null) {
            return false;
        }
        String prefix = "companies/" + company.getId() + "/logo-";
        return storageKey.startsWith(prefix)
                && (storageKey.endsWith(".png") || storageKey.endsWith(".jpg") || storageKey.endsWith(".webp"));
    }
}
