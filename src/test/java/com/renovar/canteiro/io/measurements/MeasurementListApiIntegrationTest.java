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
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
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
import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeasurementListApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private FinalCustomerRepository finalCustomerRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private MeasurementRepository measurementRepository;
    @Autowired private MeasurementVersionRepository measurementVersionRepository;
    @Autowired private MeasurementItemRepository measurementItemRepository;
    @Autowired private MeasurementServiceRepository measurementServiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    @Test
    void listsOnlyTenantMeasurementsWithExplicitFiltersPaginationAndAllowedOrdering() throws Exception {
        Company company = createCompany("measurement-list");
        Company otherCompany = createCompany("measurement-list-other");
        User reader = createUser(company, "measurement-reader@example.com");
        User otherReader = createUser(otherCompany, "measurement-other-reader@example.com");
        grant(reader, company, AccessAction.READ);
        grant(otherReader, otherCompany, AccessAction.READ);

        Work work = createWork(company, "Obra principal");
        Work otherWork = createWork(otherCompany, "Obra externa");
        Measurement older = measurementRepository.save(Measurement.create(company.getId(), work.getId(), null,
                "MED-001", "Medição de fundação", LocalDate.of(2026, 7, 10)));
        Measurement newer = measurementRepository.save(Measurement.create(company.getId(), work.getId(), null,
                "MED-002", "Medição de estrutura", LocalDate.of(2026, 7, 20)));
        measurementRepository.save(Measurement.create(otherCompany.getId(), otherWork.getId(), null,
                "MED-EXT-001", "Medição externa", LocalDate.of(2026, 7, 30)));

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("workId", work.getId().toString())
                        .param("reference", "med")
                        .param("status", "DRAFT")
                        .param("measuredOnFrom", "2026-07-01")
                        .param("measuredOnTo", "2026-07-31")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "measuredOn,desc")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.items[0].workId").value(work.getId().toString()))
                .andExpect(jsonPath("$.items[0].contractId").isEmpty())
                .andExpect(jsonPath("$.items[0].title").value("MED-002"))
                .andExpect(jsonPath("$.items[0].reference").value("MED-002"))
                .andExpect(jsonPath("$.items[0].description").value("Medição de estrutura"))
                .andExpect(jsonPath("$.items[0].measuredOn").value("2026-07-20"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("workId", work.getId().toString())
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "measuredOn,desc")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(older.getId().toString()));

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("workId", work.getId().toString())
                        .with(jwt().jwt(token -> token.subject(otherReader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void deniesMeasurementListingWithoutMeasurementsRead() throws Exception {
        Company company = createCompany("measurement-list-forbidden");
        User user = createUser(company, "measurement-no-read@example.com");

        mockMvc.perform(get("/api/v1/company/measurements")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @Transactional
    void exposesTheTenantVersionTimelineAndHistoricalVersionDetails() throws Exception {
        Company company = createCompany("measurement-history");
        Company otherCompany = createCompany("measurement-history-other");
        User reader = createUser(company, "measurement-history-reader@example.com");
        User otherReader = createUser(otherCompany, "measurement-history-other-reader@example.com");
        grant(reader, company, AccessAction.READ);
        grant(otherReader, otherCompany, AccessAction.READ);

        Measurement measurement = measurementRepository.save(Measurement.create(company.getId(), null, null,
                "Medição incremental", "MED-HIST-1", null, LocalDate.of(2026, 10, 5)));
        MeasurementVersion accepted = measurementVersionRepository.save(
                MeasurementVersion.create(company.getId(), measurement.getId(), 1));
        measurementItemRepository.save(MeasurementItem.createLinearMeter(company.getId(), accepted.getId(), 1,
                "Tubulação", "Trecho aceito", null, LocalDate.of(2026, 10, 5), new BigDecimal("2.0000"), new BigDecimal("25.00")));
        accepted.markSent();
        accepted.markPendingAcceptance();
        accepted.recordExternalAcceptance(true, LocalDate.of(2026, 10, 5), null);
        accepted = measurementVersionRepository.save(accepted);
        MeasurementVersion draft = measurementVersionRepository.save(MeasurementVersion.createRevision(company.getId(),
                measurement.getId(), 2, accepted.getId()));
        measurementItemRepository.save(MeasurementItem.createLinearMeter(company.getId(), draft.getId(), 1,
                "Tubulação", "Complemento", null, LocalDate.of(2026, 10, 5), new BigDecimal("1.0000"), new BigDecimal("25.00")));
        // Stable service groups are recorded only for the accepted version. The common draft remains incremental.
        measurementServiceRepository.save(MeasurementService.create(company.getId(), accepted.getId(), "Tubulação", 1));

        mockMvc.perform(get("/api/v1/company/measurements/{measurementId}/versions", measurement.getId())
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(draft.getId().toString()))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].previousVersionId").value(accepted.getId().toString()))
                .andExpect(jsonPath("$[0].grossAmount").value("25.00"))
                .andExpect(jsonPath("$[1].id").value(accepted.getId().toString()))
                .andExpect(jsonPath("$[1].grossAmount").value("50.00"));

        mockMvc.perform(get("/api/v1/company/measurements/{measurementId}/versions/{versionId}", measurement.getId(),
                        accepted.getId()).with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.id").value(accepted.getId().toString()))
                .andExpect(jsonPath("$.version.versionNumber").value(1))
                .andExpect(jsonPath("$.items[0].activity").value("Trecho aceito"))
                .andExpect(jsonPath("$.services[0].serviceTitle").value("Tubulação"))
                .andExpect(jsonPath("$.grossAmount").value("50.00"));

        mockMvc.perform(get("/api/v1/company/measurements/{measurementId}/versions/{versionId}/services", measurement.getId(),
                        accepted.getId()).with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceTitle").value("Tubulação"))
                .andExpect(jsonPath("$[0].sortOrder").value(1));

        mockMvc.perform(get("/api/v1/company/measurements/{measurementId}/versions", measurement.getId())
                        .with(jwt().jwt(token -> token.subject(otherReader.getId().toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void deniesVersionHistoryWithoutMeasurementsRead() throws Exception {
        Company company = createCompany("measurement-history-forbidden");
        User user = createUser(company, "measurement-history-no-read@example.com");
        Measurement measurement = measurementRepository.save(Measurement.create(company.getId(), null, null,
                "Medição restrita", "MED-HIST-2", null, LocalDate.of(2026, 10, 5)));

        mockMvc.perform(get("/api/v1/company/measurements/{measurementId}/versions", measurement.getId())
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void rejectsInvalidPaginationSortingAndDateFiltersWithProblemDetails() throws Exception {
        Company company = createCompany("measurement-list-invalid");
        User reader = createUser(company, "measurement-invalid-reader@example.com");
        grant(reader, company, AccessAction.READ);

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("page", "-1")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("size", "101")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("sort", "companyId,asc")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));

        mockMvc.perform(get("/api/v1/company/measurements")
                        .param("measuredOnFrom", "2026-08-02")
                        .param("measuredOnTo", "2026-08-01")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    }

    private void grant(User user, Company company, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), "Measurement role " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(AccessModule.MEASUREMENTS, action).orElseThrow();
            rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create("Company " + suffix, null,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.com", null, null, null));
    }

    private Work createWork(Company company, String name) {
        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL,
                "Customer " + UUID.randomUUID(), nextLegalDocument()));
        return workRepository.save(Work.create(company.getId(), customer.getId(), name, null,
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
    }

    private User createUser(Company company, String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash("Canteiro#2026Seguro"), Instant.now());
        User persisted = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persisted.getId(), company.getId()));
        return persisted;
    }

    private String nextLegalDocument() {
        return String.format("%014d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000_000L));
    }
}
