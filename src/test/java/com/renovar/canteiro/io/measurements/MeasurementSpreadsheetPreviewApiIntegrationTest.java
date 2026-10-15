package com.renovar.canteiro.io.measurements;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeasurementSpreadsheetPreviewApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private MeasurementRepository measurementRepository;
    @Autowired private MeasurementVersionRepository measurementVersionRepository;
    @Autowired private MeasurementItemRepository measurementItemRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private StorageProvider storageProvider;

    @Test
    void streamsInlinePdfPreviewForAuthorizedTenantAndAuditsTheExport() throws Exception {
        Company company = createCompany("preview-owner");
        User exporter = createUser(company, "preview-exporter@example.com");
        grant(exporter, company, AccessAction.EXPORT);
        MeasurementVersion version = createMeasurementVersion(company);

        var pending = mockMvc.perform(preview(version)
                        .with(jwt().jwt(token -> token.subject(exporter.getId().toString()))))
                .andExpect(request().asyncStarted()).andReturn();
        var result=mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("spreadsheet-preview.pdf")))
                .andReturn();

        assertTrue(result.getResponse().getContentAsByteArray().length > 4);
        assertTrue(new String(result.getResponse().getContentAsByteArray(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                .startsWith("%PDF"));
        try (var document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Company preview-owner"));
            assertTrue(text.contains("SERVIÇO: Linha de vinhaça"));
            assertTrue(text.contains("SERVIÇO: Linha de álcool"));
        }
        assertTrue(auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 100)).getContent().stream()
                .anyMatch(event -> event.getModule() == AuditModule.MEASUREMENTS
                        && event.getAction() == AuditAction.EXPORT
                        && "SPREADSHEET_PREVIEW_PDF".equals(event.getMetadata().values().get("format"))));
    }

    @Test
    void rendersPrivateCompanyLogoAndGroupsInTheSpreadsheetPreview() throws Exception {
        Company company = createCompany("preview-logo");
        storePrivateLogo(company);
        company.update(null, null, "11222333000181", null, null, null, null);
        companyRepository.save(company);
        User exporter = createUser(company, "preview-logo-exporter@example.com");
        grant(exporter, company, AccessAction.EXPORT);
        MeasurementVersion version = createMeasurementVersion(company);

        var pending = mockMvc.perform(preview(version)
                        .with(jwt().jwt(token -> token.subject(exporter.getId().toString()))))
                .andExpect(request().asyncStarted()).andReturn();
        var result=mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();

        try (var document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Medição de prévia"));
            assertTrue(text.contains("Company preview-logo"));
            assertTrue(text.contains("CNPJ: 11.222.333/0001-81"));
            assertTrue(text.contains("SERVIÇO: Linha de vinhaça"));
            assertTrue(text.contains("SERVIÇO: Linha de álcool"));
            assertTrue(hasImage(document));
        }
    }

    @Test
    void deniesPreviewWithoutMeasurementsExportPermission() throws Exception {
        Company company = createCompany("preview-forbidden");
        User user = createUser(company, "preview-no-export@example.com");
        MeasurementVersion version = createMeasurementVersion(company);

        mockMvc.perform(preview(version).with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void hidesPreviewOutsideTheAuthenticatedTenant() throws Exception {
        Company owner = createCompany("preview-owner-tenant");
        Company other = createCompany("preview-other-tenant");
        User exporterFromOtherTenant = createUser(other, "preview-other-exporter@example.com");
        grant(exporterFromOtherTenant, other, AccessAction.EXPORT);
        MeasurementVersion version = createMeasurementVersion(owner);

        mockMvc.perform(preview(version)
                        .with(jwt().jwt(token -> token.subject(exporterFromOtherTenant.getId().toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder preview(MeasurementVersion version) {
        return get("/api/v1/company/measurements/{measurementId}/versions/{versionId}/spreadsheet/preview",
                version.getMeasurementId(), version.getId());
    }

    private MeasurementVersion createMeasurementVersion(Company company) {
        Measurement measurement = measurementRepository.save(Measurement.create(company.getId(), null, null,
                "Medição de prévia", "PREVIEW-001", "Prévia de planilha", LocalDate.of(2026, 10, 5)));
        MeasurementVersion version = measurementVersionRepository.save(MeasurementVersion.create(company.getId(), measurement.getId(), 1));
        measurementItemRepository.save(MeasurementItem.createSquareMeter(company.getId(), version.getId(), 1,
                "Linha de vinhaça", "Atividade de prévia", null, LocalDate.of(2026, 10, 5),
                new BigDecimal("10.0000"), new BigDecimal("25.00")));
        measurementItemRepository.save(MeasurementItem.createSquareMeter(company.getId(), version.getId(), 2,
                "Linha de álcool", "Atividade complementar", null, LocalDate.of(2026, 10, 5),
                new BigDecimal("2.0000"), new BigDecimal("25.00")));
        return version;
    }

    private void storePrivateLogo(Company company) throws Exception {
        String storageKey = "companies/" + company.getId() + "/logo-preview.png";
        storageProvider.store(storageKey, pngLogo());
        company.updateLogo(storageKey);
        companyRepository.save(company);
    }

    private boolean hasImage(org.apache.pdfbox.pdmodel.PDDocument document) throws java.io.IOException {
        for (COSName name : document.getPage(0).getResources().getXObjectNames()) {
            PDXObject object = document.getPage(0).getResources().getXObject(name);
            if (object instanceof PDImageXObject) {
                return true;
            }
        }
        return false;
    }

    private byte[] pngLogo() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF006D77);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private void grant(User user, Company company, AccessAction action) {
        Role role = roleRepository.save(Role.create(company.getId(), "Preview role " + UUID.randomUUID(), null));
        Permission permission = permissionRepository.findByModuleAndAction(AccessModule.MEASUREMENTS, action).orElseThrow();
        rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create("Company " + suffix, null,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.com", null, null, null));
    }

    private User createUser(Company company, String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash("Canteiro#2026Seguro"), Instant.now());
        User persisted = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persisted.getId(), company.getId()));
        return persisted;
    }
}
