package com.renovar.canteiro.io.platform.support;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.application.RecordSupportReportDeliveryCommand;
import com.renovar.canteiro.io.platform.support.application.SupportActivityAuditService;
import com.renovar.canteiro.io.platform.support.application.SupportTargetContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.platform.support.domain.SupportOperation;
import com.renovar.canteiro.io.platform.support.domain.SupportTargetContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupportActivityAuditIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private SupportActivityAuditService supportActivityAuditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private PlatformOperatorContextHolder platformOperatorContextHolder;

    @Autowired
    private SupportTargetContextHolder supportTargetContextHolder;

    @AfterEach
    void clearSupportContexts() {
        platformOperatorContextHolder.clear();
        supportTargetContextHolder.clear();
    }

    @Test
    void recordsOperationalSupportActionAndReportDeliveryForTheTargetCompany() {
        Company company = createCompany();
        PlatformUser supportUser = createPlatformSupportUser();
        setSupportContexts(supportUser, company);
        UUID contractId = UUID.randomUUID();

        supportActivityAuditService.recordOperationalAction(
                SupportOperation.UPDATE_OPERATIONAL_DATA,
                AuditModule.CONTRACTS,
                "Contract",
                contractId,
                java.util.Map.of("status", "DRAFT"),
                java.util.Map.of("status", "ACTIVE")
        );
        supportActivityAuditService.recordReportSent(new RecordSupportReportDeliveryCommand(
                "Billing control",
                "finance@example.com",
                "reports/billing-control-2026-08.xlsx"
        ));

        List<AuditEvent> events = auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 20)).getContent();
        AuditEvent supportAction = events.stream()
                .filter(event -> event.getAction() == AuditAction.SUPPORT_ACTION)
                .findFirst()
                .orElseThrow();
        AuditEvent reportDelivery = events.stream()
                .filter(event -> event.getAction() == AuditAction.SEND_REPORT)
                .findFirst()
                .orElseThrow();

        assertEquals(company.getId(), supportAction.getCompanyId());
        assertEquals(supportUser.getUserId(), supportAction.getActorUserId());
        assertEquals(AuditActorType.PLATFORM_USER, supportAction.getActorType());
        assertEquals(AuditModule.CONTRACTS, supportAction.getModule());
        assertEquals("UPDATE_OPERATIONAL_DATA", supportAction.getMetadata().values().get("supportOperation"));
        assertEquals(company.getId().toString(), supportAction.getMetadata().values().get("targetCompanyId"));
        assertEquals(contractId, supportAction.getEntityId());
        assertEquals("DRAFT", supportAction.getBeforeData().values().get("status"));
        assertEquals("ACTIVE", supportAction.getAfterData().values().get("status"));
        assertEquals(AuditModule.REPORTING, reportDelivery.getModule());
        assertEquals("SEND_REPORT", reportDelivery.getMetadata().values().get("supportOperation"));
        assertEquals("finance@example.com", reportDelivery.getMetadata().values().get("recipientEmail"));
        assertEquals("reports/billing-control-2026-08.xlsx", reportDelivery.getMetadata().values().get("artifactReference"));
    }

    @Test
    void rejectsForbiddenSupportOperationBeforeRecordingAnEvent() {
        Company company = createCompany();
        PlatformUser supportUser = createPlatformSupportUser();
        setSupportContexts(supportUser, company);

        assertThrows(AccessDeniedException.class, () -> supportActivityAuditService.recordOperationalAction(
                SupportOperation.APPROVE_CHANGE,
                AuditModule.CONTRACTS,
                "Contract",
                UUID.randomUUID(),
                null,
                null
        ));
        assertEquals(0, auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 20)).getTotalElements());
    }

    private void setSupportContexts(PlatformUser supportUser, Company company) {
        platformOperatorContextHolder.setCurrentOperator(new PlatformOperatorContext(
                supportUser.getUserId(),
                supportUser.getId(),
                supportUser.getGlobalRole()
        ));
        supportTargetContextHolder.setCurrentTarget(new SupportTargetContext(
                supportUser.getUserId(),
                supportUser.getId(),
                supportUser.getGlobalRole(),
                company.getId()
        ));
    }

    private PlatformUser createPlatformSupportUser() {
        User user = User.create(UUID.randomUUID() + "@example.com", UserType.PLATFORM);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        return platformUserRepository.save(PlatformUser.create(persistedUser.getId(), PlatformUserRole.PLATFORM_SUPPORT));
    }

    private Company createCompany() {
        String suffix = UUID.randomUUID().toString();
        return companyRepository.save(Company.create(
                "Support audit " + suffix,
                "Support audit",
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                suffix + "@example.com",
                null,
                null,
                null
        ));
    }
}
