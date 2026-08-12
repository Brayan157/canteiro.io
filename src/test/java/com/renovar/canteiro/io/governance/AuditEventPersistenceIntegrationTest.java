package com.renovar.canteiro.io.governance;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditEventPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appendsAuditEventsAndPreventsUpdatesOrDeletesAtTheDatabaseLevel() {
        Company company = createCompany("Audit");
        User actor = userRepository.save(User.create("audit-actor@example.com", UserType.COMPANY));
        UUID contractId = UUID.randomUUID();
        AuditEvent persistedEvent = auditEventRepository.append(AuditEvent.create(
                company.getId(),
                actor.getId(),
                AuditActorType.COMPANY_USER,
                AuditModule.CONTRACTS,
                AuditAction.UPDATE,
                "Contract",
                contractId,
                new AuditPayload(Map.of("status", "DRAFT")),
                new AuditPayload(Map.of("status", "ACTIVE")),
                new AuditPayload(Map.of("correlationId", UUID.randomUUID().toString())),
                Instant.parse("2026-08-11T18:00:00Z")
        ));

        assertNotNull(persistedEvent.getId());
        assertEquals("ACTIVE", auditEventRepository.findByIdAndCompanyId(persistedEvent.getId(), company.getId())
                .orElseThrow()
                .getAfterData()
                .values()
                .get("status"));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE audit_event SET entity_type = ? WHERE id = ?",
                "ChangedContract",
                persistedEvent.getId()
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM audit_event WHERE id = ?",
                persistedEvent.getId()
        ));
        assertThrows(DataAccessException.class, () -> auditEventRepository.append(persistedEvent));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create(
                suffix + " Ltda.",
                suffix,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                UUID.randomUUID() + "@example.com",
                null,
                null,
                null
        ));
    }
}
