package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MeasurementCompanyBrandingLoaderTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final StorageProvider storage = mock(StorageProvider.class);
    private final MeasurementCompanyBrandingLoader loader = new MeasurementCompanyBrandingLoader(companies, storage);

    @Test
    void keepsRegisteredDocumentAndNameTogetherWithThePrivateTenantLogo() {
        UUID id = UUID.randomUUID();
        String key = "companies/" + id + "/logo-test.png";
        when(companies.findById(id)).thenReturn(Optional.of(company(id, "Nome fantasia", "11222333000181", key)));
        byte[] bytes = {1, 2, 3};
        when(storage.load(key)).thenReturn(bytes);
        var result = loader.load(id);
        assertEquals("Nome fantasia", result.displayName());
        assertEquals("11222333000181", result.document());
        assertArrayEquals(bytes, result.logoContent());
        verify(companies).findById(id);
        result.logoContent()[0] = 99;
        assertEquals(1, result.logoContent()[0]);
    }

    @Test
    void fallsBackToCorporateNameWithoutDroppingTheRegisteredDocument() {
        UUID id = UUID.randomUUID();
        when(companies.findById(id)).thenReturn(Optional.of(company(id, " ", "12ABC34501DE35", null)));
        var result = loader.load(id);
        assertEquals("Razão social cadastrada", result.displayName());
        assertEquals("12ABC34501DE35", result.document());
        assertNull(result.logoContent());
        verifyNoInteractions(storage);
    }

    @Test
    void missingLogoObjectDoesNotHideTheCompanyIdentification() {
        UUID id = UUID.randomUUID();
        String key = "companies/" + id + "/logo-unavailable.png";
        when(companies.findById(id)).thenReturn(Optional.of(company(id, "Emitente", "11222333000181", key)));
        when(storage.load(key)).thenThrow(new IllegalStateException("Unavailable private object"));
        var result = loader.load(id);
        assertEquals("Emitente", result.displayName());
        assertEquals("11222333000181", result.document());
        assertNull(result.logoContent());
    }

    @Test
    void neverLoadsAnotherCompanyLogoAndPreservesAnAbsentDocument() {
        UUID id = UUID.randomUUID();
        when(companies.findById(id)).thenReturn(Optional.of(company(id, "Emitente", null,
                "companies/" + UUID.randomUUID() + "/logo-other.png")));
        var result = loader.load(id);
        assertNull(result.document());
        assertNull(result.logoContent());
        verifyNoInteractions(storage);
    }

    @Test
    void missingCompanyCannotFallBackToAnotherTenantOrSampleData() {
        UUID id = UUID.randomUUID();
        when(companies.findById(id)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> loader.load(id));
        verifyNoInteractions(storage);
    }

    private Company company(UUID id, String tradeName, String document, String logo) {
        return Company.rehydrate(id, "Razão social cadastrada", tradeName, document, "company@example.com",
                null, null, logo, true, Instant.now(), Instant.now());
    }
}
