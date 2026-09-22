package com.renovar.canteiro.io.employees;

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
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeePaymentAccountJpaEntity;
import com.renovar.canteiro.io.employees.infrastructure.persistence.EmployeePaymentAccountJpaRepository;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EmployeeApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeePaymentAccountJpaRepository paymentAccountRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void createsEmployeeWithAnOperationalProfileWithoutEmailOrUserWhenAuthorized() throws Exception {
        Company company = createCompany("first");
        User manager = createUser(company, "manager-first@example.com");
        grant(manager, company, AccessAction.CREATE_DIRECT, AccessAction.READ);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Ana da Silva","preferredName":"Ana","jobTitle":"Montadora",
                                "department":"Operações","phone":"11999999999","registrationNumber":"MAT-001",
                                "documentType":"CPF","documentNumber":"529.982.247-25","birthDate":"1990-05-20",
                                "employmentType":"EMPLOYEE","employmentStartDate":"2025-02-03","currentlyEmployed":true,
                                "gender":"FEMALE","compensationFrequency":"MONTHLY","compensationAmount":"3500.00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.fullName").value("Ana da Silva"))
                .andExpect(jsonPath("$.employee.documentNumber").value("52998224725"))
                .andExpect(jsonPath("$.employee.compensationAmount").value("3500.00"))
                .andExpect(jsonPath("$.employee.userId").isEmpty());
    }

    @Test
    void preventsAUserFromAnotherTenantFromReadingEmployee() throws Exception {
        Company firstCompany = createCompany("second-first");
        Company secondCompany = createCompany("second-other");
        User firstManager = createUser(firstCompany, "manager-second-first@example.com");
        User otherManager = createUser(secondCompany, "manager-second-other@example.com");
        grant(firstManager, firstCompany, AccessAction.CREATE_DIRECT, AccessAction.READ);
        grant(otherManager, secondCompany, AccessAction.READ);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(firstManager.getId().toString())))
                        .contentType("application/json")
                        .content(employeeRequestJson()))
                .andExpect(status().isCreated());
        UUID employeeId = employeeRepository.findByCompanyId(firstCompany.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst().getId();

        mockMvc.perform(get("/api/v1/company/employees/{employeeId}", employeeId)
                        .with(jwt().jwt(token -> token.subject(otherManager.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void preventsAnotherTenantFromManagingEmployeeAddressesOrPaymentReferences() throws Exception {
        Company firstCompany = createCompany("private-details-first");
        Company secondCompany = createCompany("private-details-other");
        User firstManager = createUser(firstCompany, "manager-private-details-first@example.com");
        User otherManager = createUser(secondCompany, "manager-private-details-other@example.com");
        grant(firstManager, firstCompany, AccessAction.CREATE_DIRECT, AccessAction.READ);
        grant(otherManager, secondCompany, AccessAction.CREATE_DIRECT, AccessAction.VIEW_PAYMENT_DETAILS);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(firstManager.getId().toString())))
                        .contentType("application/json")
                        .content(employeeRequestJson()))
                .andExpect(status().isCreated());
        UUID employeeId = employeeRepository.findByCompanyId(firstCompany.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst().getId();

        mockMvc.perform(post("/api/v1/company/employees/{employeeId}/addresses", employeeId)
                        .with(jwt().jwt(token -> token.subject(otherManager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"street\":\"Rua A\",\"city\":\"São Paulo\",\"country\":\"BR\",\"primaryAddress\":true}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/company/employees/{employeeId}/payment-accounts", employeeId)
                        .with(jwt().jwt(token -> token.subject(firstManager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"accountType\":\"PAYROLL\",\"accountNumber\":\"12345678\",\"accountCheckDigit\":\"1\"}"))
                .andExpect(status().isCreated());
        UUID paymentAccountId = paymentAccountRepository
                .findByCompanyIdAndEmployeeIdAndActiveTrueOrderByCreatedAtAsc(firstCompany.getId(), employeeId).getFirst().getId();

        mockMvc.perform(post("/api/v1/company/employees/{employeeId}/payment-accounts/{accountId}/payment-details/reveal",
                        employeeId, paymentAccountId)
                        .with(jwt().jwt(token -> token.subject(otherManager.getId().toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/company/employees/{employeeId}/payment-accounts", employeeId)
                        .with(jwt().jwt(token -> token.subject(otherManager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"accountType\":\"PAYROLL\",\"accountNumber\":\"12345678\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmployeeWithoutAnIdentificationDocument() throws Exception {
        Company company = createCompany("document-required");
        User manager = createUser(company, "manager-document-required@example.com");
        grant(manager, company, AccessAction.CREATE_DIRECT);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Ana da Silva","jobTitle":"Montadora","birthDate":"1990-05-20",
                                "employmentType":"EMPLOYEE","employmentStartDate":"2025-02-03","currentlyEmployed":true,
                                "gender":"FEMALE","compensationFrequency":"MONTHLY","compensationAmount":"3500.00"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revealsCompletePaymentDetailsOnlyWithTheDedicatedPermissionAndAuditsTheConsultation() throws Exception {
        Company company = createCompany("payment-reveal");
        User manager = createUser(company, "manager-payment-reveal@example.com");
        User observer = createUser(company, "observer-payment-reveal@example.com");
        grant(manager, company, AccessAction.CREATE_DIRECT, AccessAction.VIEW_PAYMENT_DETAILS);
        grant(observer, company, AccessAction.READ);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content(employeeRequestJson()))
                .andExpect(status().isCreated());
        UUID employeeId = employeeRepository.findByCompanyId(company.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst().getId();

        mockMvc.perform(post("/api/v1/company/employees/{employeeId}/payment-accounts", employeeId)
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"bankName":"Banco Exemplo","bankCode":"999","branch":"0001",
                                "accountType":"PAYROLL","accountNumber":"12345678","accountCheckDigit":"X",
                                "pixKeyType":"EMAIL","pixKey":"ana@example.com",
                                "pixReceiverName":"Ana da Silva","pixReceiverCity":"São Paulo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumberMasked").value("***5678"))
                .andExpect(jsonPath("$.pixKeyMasked").value("***.com"));
        EmployeePaymentAccountJpaEntity account = paymentAccountRepository
                .findByCompanyIdAndEmployeeIdAndActiveTrueOrderByCreatedAtAsc(company.getId(), employeeId).getFirst();
        String revealUrl = "/api/v1/company/employees/" + employeeId + "/payment-accounts/" + account.getId()
                + "/payment-details/reveal";

        mockMvc.perform(post(revealUrl).with(jwt().jwt(token -> token.subject(observer.getId().toString()))))
                .andExpect(status().isForbidden());

        String revealBody = mockMvc.perform(post(revealUrl).with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("12345678"))
                .andExpect(jsonPath("$.accountCheckDigit").value("X"))
                .andExpect(jsonPath("$.pixKey").value("ana@example.com"))
                .andExpect(jsonPath("$.pixCopyPaste").isNotEmpty())
                .andExpect(jsonPath("$.pixQrCode").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andReturn().getResponse().getContentAsString();
        String payload = new tools.jackson.databind.ObjectMapper().readTree(revealBody).path("pixCopyPaste").asText();
        org.junit.jupiter.api.Assertions.assertTrue(payload.startsWith("000201"));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("0014br.gov.bcb.pix"));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("52040000"));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("5303986"));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("62070503***"));
        org.junit.jupiter.api.Assertions.assertFalse(hasTopLevelField(payload, "54"));
        org.junit.jupiter.api.Assertions.assertEquals(payload.substring(payload.length() - 4), crc16(payload.substring(0, payload.length() - 4)));
        org.junit.jupiter.api.Assertions.assertTrue(auditEventRepository.findByCompanyId(company.getId(),
                        org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .anyMatch(event -> event.getAction() == AuditAction.VIEW_SENSITIVE
                        && "EmployeePaymentAccount".equals(event.getEntityType())
                        && !event.getMetadata().values().toString().contains("12345678")
                        && !event.getMetadata().values().toString().contains("ana@example.com")
                        && event.getBeforeData() == null && event.getAfterData() == null));
    }

    private static String employeeRequestJson() {
        return """
                {"fullName":"Ana da Silva","jobTitle":"Montadora","documentType":"CPF",
                "documentNumber":"529.982.247-25","birthDate":"1990-05-20","employmentType":"EMPLOYEE",
                "employmentStartDate":"2025-02-03","currentlyEmployed":true,"gender":"FEMALE",
                "compensationFrequency":"MONTHLY","compensationAmount":"3500.00"}
                """;
    }

    private static String crc16(String value) {
        int crc = 0xFFFF;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (current & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(java.util.Locale.ROOT, "%04X", crc);
    }

    private static boolean hasTopLevelField(String payload, String expectedField) {
        int index = 0;
        while (index + 4 <= payload.length()) {
            String field = payload.substring(index, index + 2);
            if ("63".equals(field)) return false;
            int length = Integer.parseInt(payload.substring(index + 2, index + 4));
            if (expectedField.equals(field)) return true;
            index += 4 + length;
        }
        return false;
    }

    private void grant(User user, Company company, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), "Employee role " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(AccessModule.EMPLOYEES, action).orElseThrow();
            rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create("Company " + suffix, null,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.com", null, null, null));
    }

    private User createUser(Company company, String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash("Canteiro#2026Seguro"), Instant.now());
        User persistedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persistedUser.getId(), company.getId()));
        return persistedUser;
    }
}
