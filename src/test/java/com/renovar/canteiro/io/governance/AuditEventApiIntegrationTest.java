package com.renovar.canteiro.io.governance;

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
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.AuditPresentation;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditEventApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private com.renovar.canteiro.io.identity.application.PasswordHasher passwords;

    @Test
    void listsOnlySafeTenantAuditHistoryInDefaultOccurredAtDescendingOrder() throws Exception {
        Company company = createCompany("Audit history");
        Company otherCompany = createCompany("Other audit history");
        User reader = createCompanyUser(company);
        linkEmployeeProfile(company, reader, "Ana Auditora");
        grantAuditRead(reader, company);
        UUID entityId = UUID.randomUUID();
        AuditEvent older = appendEvent(company, reader, entityId, Instant.parse("2026-09-01T10:00:00Z"));
        AuditEvent newer = appendEvent(company, reader, UUID.randomUUID(), Instant.parse("2026-09-02T10:00:00Z"));
        appendEvent(otherCompany, createCompanyUser(otherCompany), UUID.randomUUID(), Instant.parse("2026-09-03T10:00:00Z"));

        mockMvc.perform(get("/api/v1/company/audit-events")
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.items[1].occurredAt").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].actorType").value("COMPANY_USER"))
                .andExpect(jsonPath("$.items[1].actorUserId").value(reader.getId().toString()))
                .andExpect(jsonPath("$.items[1].actorDisplayName").value("Ana Auditora"))
                .andExpect(jsonPath("$.items[1].actorEmail").value(reader.getEmail()))
                .andExpect(jsonPath("$.items[1].module").value("CONTRACTS"))
                .andExpect(jsonPath("$.items[1].action").value("UPDATE"))
                .andExpect(jsonPath("$.items[1].entityType").value("Contract"))
                .andExpect(jsonPath("$.items[1].entityId").value(entityId.toString()))
                .andExpect(jsonPath("$.items[1].metadata.origin").value("integration-test"))
                .andExpect(jsonPath("$.items[1].metadata.email").doesNotExist())
                .andExpect(jsonPath("$.items[1].beforeData").doesNotExist())
                .andExpect(jsonPath("$.items[1].afterData").doesNotExist());
    }

    @Test
    void recordsOwnerAndCustomerLabelsAndRepairsOnlyScopedHistoricalPlaceholders() throws Exception {
        Company company = createCompany("Company audit names");
        Company other = createCompany("Other company audit names");
        User owner = createCompanyUser(company, true);
        User employee = createCompanyUser(company);
        User foreign = createCompanyUser(other);
        linkEmployeeProfile(company, employee, "Clara Auditoria");
        grantAuditRead(owner, company);
        grant(owner, company, AccessModule.ROLES, AccessAction.READ, AccessAction.MANAGE_ROLES);
        grant(employee, company, AccessModule.CUSTOMERS, AccessAction.CREATE_DIRECT);
        grant(foreign, other, AccessModule.CUSTOMERS, AccessAction.CREATE_DIRECT);
        var ownerAuth = jwt().jwt(token -> token.subject(owner.getId().toString()));
        mockMvc.perform(post("/api/v1/company/access/roles").with(ownerAuth)
                        .contentType("application/json").content("{\"name\":\"Equipe de auditoria\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/company/customers")
                        .with(jwt().jwt(token -> token.subject(employee.getId().toString())))
                        .contentType("application/json")
                        .content("{\"name\":\"Cliente Clara\",\"customerType\":\"LEGAL\",\"document\":\"01234567000139\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/company/customers")
                        .with(jwt().jwt(token -> token.subject(foreign.getId().toString())))
                        .contentType("application/json")
                        .content("{\"name\":\"Cliente de outro tenant\",\"customerType\":\"LEGAL\",\"document\":\"01234567000139\"}"))
                .andExpect(status().isCreated());
        var all = org.springframework.data.domain.Pageable.unpaged();
        var own = auditEventRepository.findByCompanyId(company.getId(), all).stream()
                .filter(e -> "FinalCustomer".equals(e.getEntityType())).findFirst().orElseThrow();
        var outside = auditEventRepository.findByCompanyId(other.getId(), all).stream()
                .filter(e -> "FinalCustomer".equals(e.getEntityType())).findFirst().orElseThrow();
        var placeholder = new AuditPresentation("Usuário histórico indisponível", "Registro histórico indisponível",
                null, "Evento anterior", "direct", null, null);
        var legacy = auditEventRepository.append(AuditEvent.create(company.getId(), owner.getId(),
                AuditActorType.COMPANY_USER, AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomer", own.getEntityId(),
                null, new AuditPayload(Map.of("name", "Nome registrado na época")), null, Instant.now(), placeholder));
        var crossTenant = auditEventRepository.append(AuditEvent.create(company.getId(), owner.getId(),
                AuditActorType.COMPANY_USER, AuditModule.CUSTOMERS, AuditAction.UPDATE, "FinalCustomer", outside.getEntityId(),
                null, new AuditPayload(Map.of("name", "Não deve aparecer")), null, Instant.now(), placeholder));

        mockMvc.perform(get("/api/v1/company/audit-events").with(ownerAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.module == 'ROLES')].actorDisplayName")
                        .value(org.hamcrest.Matchers.contains("Proprietário da empresa")))
                .andExpect(jsonPath("$.items[?(@.id == '" + own.getId() + "')].actorDisplayName")
                        .value(org.hamcrest.Matchers.contains("Clara Auditoria")))
                .andExpect(jsonPath("$.items[?(@.id == '" + own.getId() + "')].entityDisplayName")
                        .value(org.hamcrest.Matchers.contains("Cliente Clara")))
                .andExpect(jsonPath("$.items[?(@.id == '" + legacy.getId() + "')].entityDisplayName")
                        .value(org.hamcrest.Matchers.contains("Nome registrado na época")))
                .andExpect(jsonPath("$.items[?(@.id == '" + crossTenant.getId() + "')].entityDisplayName")
                        .value(org.hamcrest.Matchers.contains("Registro histórico indisponível")))
                .andExpect(jsonPath("$.items[*].afterData").isEmpty());
        org.assertj.core.api.Assertions.assertThat(auditEventRepository.findByCompanyId(company.getId(), all).stream()
                .filter(e -> e.getId().equals(legacy.getId())).findFirst().orElseThrow().getPresentation()).isEqualTo(placeholder);
    }

    @Test
    void namesRoleAndRoleAssignmentEventsWithinTheEventCompany() throws Exception {
        Company company = createCompany("Access audit");
        Company other = createCompany("Other access audit");
        User owner = createCompanyUser(company, true);
        User employee = createCompanyUser(company);
        User foreign = createCompanyUser(other);
        linkEmployeeProfile(company, employee, "Clara Operações");
        linkEmployeeProfile(other, foreign, "Funcionária de outra empresa");
        grantAuditRead(owner, company);
        grant(owner, company, AccessModule.ROLES, AccessAction.READ, AccessAction.MANAGE_ROLES);
        grant(owner, company, AccessModule.USERS, AccessAction.READ, AccessAction.MANAGE_USERS);
        var ownerAuth = jwt().jwt(token -> token.subject(owner.getId().toString()));
        String base = "/api/v1/company/access";
        var all = org.springframework.data.domain.Pageable.unpaged();
        mockMvc.perform(post(base + "/roles").with(ownerAuth)
                        .contentType("application/json").content("{\"name\":\"Operação inicial\"}"))
                .andExpect(status().isCreated());
        UUID roleId = roleRepository.findByCompanyId(company.getId(), all).stream()
                .filter(role -> "Operação inicial".equals(role.getName())).findFirst().orElseThrow().getId();
        mockMvc.perform(put(base + "/roles/" + roleId).with(ownerAuth)
                        .contentType("application/json").content("{\"name\":\"Operação revisada\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(base + "/roles/" + roleId + "/permissions").with(ownerAuth)
                        .contentType("application/json").content("{\"permissionIds\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(base + "/users/" + employee.getId() + "/roles").with(ownerAuth)
                        .contentType("application/json").content("{\"roleIds\":[\"" + roleId + "\"]}"))
                .andExpect(status().isOk());
        var placeholder = new AuditPresentation("Proprietário da empresa", "Registro histórico indisponível",
                null, "Evento anterior", "direct", null, null);
        var oldRole = auditEventRepository.append(AuditEvent.create(company.getId(), owner.getId(),
                AuditActorType.COMPANY_USER, AuditModule.ROLES, AuditAction.UPDATE, "Role", roleId,
                null, new AuditPayload(Map.of("name", "Nome histórico do papel")), null, Instant.now(), placeholder));
        var foreignRole = roleRepository.save(Role.create(other.getId(), "Papel de outra empresa", null));
        auditEventRepository.append(AuditEvent.create(company.getId(), owner.getId(), AuditActorType.COMPANY_USER,
                AuditModule.ROLES, AuditAction.UPDATE, "RolePermissions", foreignRole.getId(),
                null, null, null, Instant.now(), placeholder));
        auditEventRepository.append(AuditEvent.create(company.getId(), owner.getId(), AuditActorType.COMPANY_USER,
                AuditModule.USERS, AuditAction.UPDATE, "UserRoles", foreign.getId(),
                null, null, null, Instant.now(), placeholder));

        mockMvc.perform(get("/api/v1/company/audit-events").with(ownerAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.entityType == 'Role')].entityDisplayName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("Operação inicial", "Operação revisada", "Nome histórico do papel")))
                .andExpect(jsonPath("$.items[?(@.entityType == 'RolePermissions')].entityDisplayName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("Operação revisada", "Registro histórico indisponível")))
                .andExpect(jsonPath("$.items[?(@.entityType == 'UserRoles')].entityDisplayName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("Clara Operações", "Registro histórico indisponível")))
                .andExpect(jsonPath("$.items[*].afterData").isEmpty());
        org.assertj.core.api.Assertions.assertThat(auditEventRepository.findByCompanyId(company.getId(), all).stream()
                .filter(e -> e.getId().equals(oldRole.getId())).findFirst().orElseThrow().getPresentation()).isEqualTo(placeholder);
    }

    @Test
    void deniesAuditHistoryWithoutAuditReadPermission() throws Exception {
        Company company = createCompany("Unauthorized audit");
        User user = createCompanyUser(company);

        mockMvc.perform(get("/api/v1/company/audit-events")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    private AuditEvent appendEvent(Company company, User actor, UUID entityId, Instant occurredAt) {
        return auditEventRepository.append(AuditEvent.create(
                company.getId(), actor.getId(), AuditActorType.COMPANY_USER,
                AuditModule.CONTRACTS, AuditAction.UPDATE, "Contract", entityId,
                new AuditPayload(Map.of("email", "protected-before@example.com")),
                new AuditPayload(Map.of("amount", "999.90", "token", "protected-after-value")),
                new AuditPayload(Map.of("origin", "integration-test", "email", "protected-metadata@example.com")),
                occurredAt
        ));
    }

    private void grantAuditRead(User user, Company company) {
        grant(user, company, AccessModule.AUDIT, AccessAction.READ);
    }

    private void grant(User user, Company company, AccessModule module, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), "Audit test " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(module, action).orElseThrow();
            rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create(
                suffix + " Ltda.", suffix, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                UUID.randomUUID() + "@example.com", null, null, null
        ));
    }

    private User createCompanyUser(Company company) {
        return createCompanyUser(company, false);
    }

    private User createCompanyUser(Company company, boolean owner) {
        User user = User.create(UUID.randomUUID() + "@example.com", UserType.COMPANY);
        user.activate(passwords.hash("Canteiro#2026Seguro"), Instant.now());
        user = userRepository.save(user);
        companyUserRepository.save(owner ? CompanyUser.createInitialOwner(user.getId(), company.getId())
                : CompanyUser.create(user.getId(), company.getId()));
        return user;
    }

    private void linkEmployeeProfile(Company company, User user, String fullName) {
        Employee employee = Employee.create(
                company.getId(), fullName, null, "Auditora", null, null, "AUD-001",
                EmployeeDocumentType.CPF, "52998224725", null, LocalDate.of(1990, 1, 20),
                EmployeeEmploymentType.EMPLOYEE, LocalDate.of(2024, 1, 2), null, true,
                EmployeeGender.FEMALE, null, EmployeeCompensationFrequency.MONTHLY, new BigDecimal("3500.00")
        );
        employee.linkUser(user.getId());
        employeeRepository.save(employee);
    }
}
