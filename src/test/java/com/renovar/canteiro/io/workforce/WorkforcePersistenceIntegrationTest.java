package com.renovar.canteiro.io.workforce;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import com.renovar.canteiro.io.workforce.application.WorkforceAdministrationService;
import com.renovar.canteiro.io.workforce.domain.EmployeeEmployment;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WorkforcePersistenceIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 10);
    private static final Instant RECORDED_AT = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkforceRepository workforce;

    @Autowired
    private WorkforceAdministrationService administration;

    @Autowired
    private TenantContextHolder tenantContext;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StorageProvider storage;

    @Test
    void attachesPrivatePhotosWithRevisionAndKeepsWithdrawnMetadata() throws Exception {
        var actor = fixture();
        var outsider = fixture();
        var requester = new Fixture(actor.companyId(), actor.workId(), createUser(actor.companyId()), actor.employeeId());
        grantRolePermissions(actor, "WORKFORCE", "MANAGE_USERS");
        grantRolePermissions(actor, "WORK_DIARY", "READ", "CREATE_DIRECT", "UPDATE_DIRECT");
        grantRolePermissions(outsider, "WORK_DIARY", "READ", "UPDATE_DIRECT");
        grantRolePermissions(requester, "WORK_DIARY", "REQUEST_UPDATE");
        grantDiaryOperations(requester.companyId(), requester.workId(), requester.userId(), WorkOperation.UPDATE);
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(), WorkOperation.READ, WorkOperation.CREATE,
                WorkOperation.UPDATE, WorkOperation.CLOSE);
        configureZone(actor);
        var auth = jwt().jwt(token -> token.subject(actor.userId().toString()));
        var otherAuth = jwt().jwt(token -> token.subject(outsider.userId().toString()));
        var requestAuth = jwt().jwt(token -> token.subject(requester.userId().toString()));
        String path = diaryPath(actor.workId());
        String photos = path + "/photos";
        String activityId = UUID.randomUUID().toString();
        String occurrenceId = UUID.randomUUID().toString();
        var activity = Map.of("id", activityId, "description", "Montagem", "entries", List.of());
        var occurrence = Map.of("id", occurrenceId, "description", "Conferência");
        String diary = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "activities", List.of(activity),
                "occurrences", List.of(occurrence)));
        mvc.perform(post(path).with(auth).contentType("application/json").content(diary)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/company/works/" + actor.workId() + "/diary/operations").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoEvidenceSupported").value(true))
                .andExpect(jsonPath("$.itemPhotoEvidenceSupported").value(true))
                .andExpect(jsonPath("$.occurrenceTimingSupported").value(true));

        byte[] bytes = photoBytes("png");
        var file = new MockMultipartFile("file", "private-original.png", "image/png", bytes);
        String operation = UUID.randomUUID().toString();
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0").with(auth))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0")
                        .param("activityId", activityId).param("occurrenceId", occurrenceId).with(auth))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0")
                        .param("activityId", UUID.randomUUID().toString()).with(auth))
                .andExpect(status().isNotFound());
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "1")
                        .param("activityId", activityId).with(auth))
                .andExpect(status().isConflict());
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0").with(requestAuth))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(photos).file(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{8}))
                        .param("operationId", operation).param("expectedRevision", "0").param("activityId", activityId).with(auth))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(photos).file(new MockMultipartFile("file", "large.png", "image/png", new byte[8 * 1024 * 1024 + 1]))
                        .param("operationId", operation).param("expectedRevision", "0").param("activityId", activityId).with(auth))
                .andExpect(status().is(413));
        var created = mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0")
                        .param("activityId", activityId).with(auth))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.activityId").value(activityId)).andExpect(jsonPath("$.occurrenceId").isEmpty())
                .andExpect(jsonPath("$.contentType").value("image/png")).andExpect(jsonPath("$.originalFilename").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist()).andReturn();
        var photoId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0")
                        .param("activityId", activityId).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(photoId.toString()));
        var replacement = new MockMultipartFile("file", "evidence.jpg", "image/jpeg", photoBytes("jpeg"));
        mvc.perform(multipart(photos).file(replacement).param("operationId", operation).param("expectedRevision", "0")
                        .param("activityId", activityId).with(auth))
                .andExpect(status().isConflict());
        mvc.perform(multipart(photos).file(file).param("operationId", operation).param("expectedRevision", "0")
                        .param("occurrenceId", occurrenceId).with(auth))
                .andExpect(status().isConflict());
        mvc.perform(multipart(photos).file(replacement).param("operationId", UUID.randomUUID().toString()).param("expectedRevision", "0")
                        .param("occurrenceId", occurrenceId).with(auth))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.occurrenceId").value(occurrenceId));
        mvc.perform(get(photos).param("occurrenceId", occurrenceId).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].occurrenceId").value(occurrenceId));
        mvc.perform(get(photos + "/" + photoId + "/content").with(auth))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(photos).with(otherAuth)).andExpect(status().isNotFound());
        mvc.perform(get(photos + "/" + photoId + "/content").with(otherAuth)).andExpect(status().isNotFound());
        var separate = new Fixture(actor.companyId(), createWork(actor.companyId()), actor.userId(), actor.employeeId());
        grantDiaryOperations(separate.companyId(), separate.workId(), separate.userId(), WorkOperation.READ, WorkOperation.CREATE);
        configureZone(separate);
        mvc.perform(post(diaryPath(separate.workId())).with(auth).contentType("application/json").content(diary))
                .andExpect(status().isCreated());
        mvc.perform(get(diaryPath(separate.workId()) + "/photos/" + photoId + "/content").with(auth))
                .andExpect(status().isNotFound());

        String corrected = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "expectedRevision", 0,
                "reason", "Conferência", "activities", List.of(activity), "occurrences", List.of()));
        mvc.perform(put(path).with(auth).contentType("application/json").content(corrected)).andExpect(status().isOk());
        mvc.perform(get(photos).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(photos).param("activityId", activityId).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(photoId.toString())).andExpect(jsonPath("$[0].revision").value(0));
        mvc.perform(get(photos).param("occurrenceId", occurrenceId).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_diary_photo WHERE company_id = ?", Integer.class,
                actor.companyId())).isEqualTo(2);
        mvc.perform(get(photos).param("activityId", activityId).param("revision", "0").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(photoId.toString()));
        mvc.perform(multipart(photos).file(file).param("operationId", UUID.randomUUID().toString()).param("expectedRevision", "1")
                        .param("occurrenceId", occurrenceId).with(auth))
                .andExpect(status().isNotFound());
        mvc.perform(put(photos + "/" + photoId + "/withdraw").with(auth).contentType("application/json")
                        .content("{\"expectedRevision\":1,\"reason\":\"Foto equivocada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.withdrawalReason").value("Foto equivocada"));
        mvc.perform(get(photos).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(photos).param("activityId", activityId).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(photos).param("revision", "0").param("activityId", activityId).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(photoId.toString())).andExpect(jsonPath("$[0].withdrawnAt").isNotEmpty());
        mvc.perform(get(photos + "/" + photoId + "/content").with(auth)).andExpect(status().isNotFound());
        String closure = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "expectedRevision", 1,
                "reason", "Revisão concluída"));
        mvc.perform(post(path + "/close").with(auth).contentType("application/json").content(closure))
                .andExpect(status().isOk());
        mvc.perform(multipart(photos).file(replacement).param("operationId", UUID.randomUUID().toString())
                        .param("expectedRevision", "2").param("activityId", activityId).with(auth))
                .andExpect(status().isConflict());
        UUID diaryId = jdbc.queryForObject("SELECT id FROM work_diary WHERE company_id = ? AND work_id = ?", UUID.class,
                actor.companyId(), actor.workId());
        UUID generalPhoto = UUID.randomUUID();
        String legacyKey = "companies/" + actor.companyId() + "/work-diaries/" + diaryId + "/photos/" + generalPhoto;
        storage.store(legacyKey, bytes);
        try {
            jdbc.update("""
                    INSERT INTO work_diary_photo (id, company_id, work_id, diary_id, revision, operation_id, content_type,
                        size_bytes, sha256, uploaded_by, uploaded_at)
                    VALUES (?, ?, ?, ?, 0, ?, 'image/png', ?, ?, ?, CURRENT_TIMESTAMP)
                    """, generalPhoto, actor.companyId(), actor.workId(), diaryId, UUID.randomUUID(), bytes.length,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), actor.userId());
            mvc.perform(get(photos).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
            mvc.perform(get(photos + "/" + generalPhoto + "/content").with(auth))
                    .andExpect(status().isOk()).andExpect(content().bytes(bytes));
            mvc.perform(get(photos).param("activityId", activityId).param("revision", "0").with(auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        } finally {
            storage.delete(legacyKey);
        }
        jdbc.update("UPDATE work_access_grant SET revoked_at = CURRENT_TIMESTAMP WHERE work_id = ? AND operation = 'READ'",
                actor.workId());
        mvc.perform(get(photos).param("revision", "0").with(auth)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_id = ? ORDER BY occurred_at", String.class, photoId))
                .containsExactly("CREATE", "VIEW_SENSITIVE", "DEACTIVATE");
    }

    private static byte[] photoBytes(String format) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, output)).isTrue();
            return output.toByteArray();
        }
    }

    @Test
    void assignsEmployeesByWorkAndStartDateAndKeepsTheirHistoryOnTransfer() throws Exception {
        var actor = fixture();
        var outsider = fixture();
        UUID otherWork = createWork(actor.companyId());
        UUID secondEmployee = createEmployee(actor.companyId());
        List<UUID> employees = List.of(actor.employeeId(), secondEmployee).stream().sorted().toList();
        grantRolePermissions(actor, "WORKFORCE", "MANAGE_USERS");
        var auth = jwt().jwt(token -> token.subject(actor.userId().toString()));
        tenantContext.setCurrentTenant(new TenantContext(actor.userId(), actor.companyId()));
        try {
            administration.recordEmployment(employees.getFirst(), FIRST_DAY, null);
        } finally {
            tenantContext.clear();
        }
        String assignments = "/api/v1/company/workforce/assignments";
        String initialBatch = json.writeValueAsString(Map.of("workId", actor.workId(), "employeeIds", employees,
                "startedOn", FIRST_DAY));
        mvc.perform(post(assignments + "/batch").with(auth).contentType("application/json").content(initialBatch))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("EMPLOYMENT_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_assignment WHERE work_id = ?", Integer.class,
                actor.workId())).isZero();

        tenantContext.setCurrentTenant(new TenantContext(actor.userId(), actor.companyId()));
        try {
            administration.recordEmployment(employees.getLast(), FIRST_DAY, null);
        } finally {
            tenantContext.clear();
        }
        var created = mvc.perform(post(assignments + "/batch").with(auth).contentType("application/json").content(initialBatch))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2)).andReturn();
        mvc.perform(post(assignments + "/batch").with(auth).contentType("application/json").content(initialBatch))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].endedOn").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_assignment WHERE work_id = ?", Integer.class,
                actor.workId())).isEqualTo(2);

        String transfer = json.writeValueAsString(Map.of("workId", otherWork, "employeeIds", List.of(employees.getFirst()),
                "startedOn", FIRST_DAY.plusDays(1)));
        mvc.perform(post(assignments + "/batch").with(auth).contentType("application/json").content(transfer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("ASSIGNMENT_PERIOD_CONFLICT"));
        String assignmentId = json.readTree(created.getResponse().getContentAsString()).get(0).get("id").asText();
        mvc.perform(put(assignments + "/" + assignmentId + "/end").with(auth).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("endedOn", FIRST_DAY, "expectedVersion", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endedOn").value(FIRST_DAY.toString()));
        mvc.perform(post(assignments + "/batch").with(auth).contentType("application/json").content(transfer))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].workId").value(otherWork.toString()));
        String foreignEmployee = json.writeValueAsString(Map.of("workId", actor.workId(),
                "employeeIds", List.of(outsider.employeeId()), "startedOn", FIRST_DAY));
        mvc.perform(post(assignments + "/batch").with(auth).contentType("application/json").content(foreignEmployee))
                .andExpect(status().isNotFound());
    }

    @Test
    void givesAnExistingOwnerOperationalPermissionsAndStillRequiresEachWorksGrant() throws Exception {
        var owner = fixture();
        var foreign = fixture();
        UUID teammate = createUser(owner.companyId());
        UUID protectedRole = UUID.randomUUID();
        jdbc.update("UPDATE company_user SET company_owner = TRUE WHERE user_id = ?", owner.userId());
        jdbc.update("INSERT INTO access_role (id, company_id, name, system_protected) VALUES (?, ?, ?, TRUE)",
                protectedRole, owner.companyId(), "Company Administrator");
        jdbc.update("INSERT INTO user_role (id, user_id, role_id, company_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), owner.userId(), protectedRole, owner.companyId());
        grantRolePermissions(owner, "ROLES", "READ");
        var auth = jwt().jwt(token -> token.subject(owner.userId().toString()));
        var teammateAuth = jwt().jwt(token -> token.subject(teammate.toString()));
        String workforcePath = "/api/v1/company/workforce";
        String operations = "/api/v1/company/works/" + owner.workId() + "/diary/operations";

        mvc.perform(get("/api/v1/auth/me").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.containsInAnyOrder(
                        "ROLES.READ", "WORKFORCE.MANAGE_USERS", "WORKFORCE.MANAGE_ROLES",
                        "WORK_DIARY.READ", "WORK_DIARY.CREATE_DIRECT", "WORK_DIARY.UPDATE_DIRECT")));
        mvc.perform(get("/api/v1/company/access/roles").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.systemProtected == true)].permissionIds")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.hasSize(5))));
        mvc.perform(get("/api/v1/auth/me").with(teammateAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
        mvc.perform(get(workforcePath + "/employments").param("employeeId", owner.employeeId().toString())
                        .with(teammateAuth))
                .andExpect(status().isForbidden());
        mvc.perform(get(operations).with(auth)).andExpect(status().isNotFound());
        mvc.perform(post(workforcePath + "/employments").with(auth).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("employeeId", owner.employeeId(), "startedOn", FIRST_DAY))))
                .andExpect(status().isCreated());

        var grant = Map.of("workId", owner.workId(), "userId", owner.userId(),
                "operation", "READ", "startsAt", RECORDED_AT);
        mvc.perform(post(workforcePath + "/grants").with(auth).contentType("application/json")
                        .content(json.writeValueAsString(grant)))
                .andExpect(status().isCreated());
        mvc.perform(get(operations).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operations.length()").value(1))
                .andExpect(jsonPath("$.operations[0].operation").value("READ"));
        grantDiaryOperations(owner.companyId(), owner.workId(), owner.userId(), WorkOperation.CREATE, WorkOperation.UPDATE);
        mvc.perform(get(operations).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operations[?(@.mode == 'DIRECT')].operation")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("CREATE", "UPDATE")));
        var foreignGrant = Map.of("workId", foreign.workId(), "userId", owner.userId(),
                "operation", "READ", "startsAt", RECORDED_AT);
        mvc.perform(post(workforcePath + "/grants").with(auth).contentType("application/json")
                        .content(json.writeValueAsString(foreignGrant)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permission WHERE role_id = ?", Integer.class,
                protectedRole)).isZero();
    }

    @Test
    void recordsMultiplePeopleWithOneSourceOfMinutesAndPreservesClosedRevisions() throws Exception {
        var first = fixture();
        var foreign = fixture();
        var sameTenantElsewhere = createWork(first.companyId());
        UUID colleague = createEmployee(first.companyId());
        jdbc.update("UPDATE employee SET user_id = ?, full_name = 'Clara Diário' WHERE id = ?", first.userId(), first.employeeId());
        grantRolePermissions(first, "WORKFORCE", "MANAGE_USERS");
        grantRolePermissions(first, "WORK_DIARY", "READ", "CREATE_DIRECT", "UPDATE_DIRECT");
        grantRolePermissions(first, "AUDIT", "READ");
        grantRolePermissions(foreign, "WORK_DIARY", "READ");
        grantRolePermissions(foreign, "AUDIT", "READ");
        grantDiaryOperations(first.companyId(), first.workId(), first.userId(), WorkOperation.READ, WorkOperation.CREATE,
                WorkOperation.UPDATE, WorkOperation.CLOSE, WorkOperation.REOPEN, WorkOperation.REGISTER_OCCASIONAL);
        registerWorkTime(first, first.employeeId(), true);
        registerWorkTime(first, colleague, false);
        configureZone(first);
        UUID serviceId = createService(first.companyId(), first.workId());
        UUID activityId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var entry1 = Map.of("id", UUID.randomUUID(), "employeeId", first.employeeId(), "minutes", 180);
        var entry2 = Map.of("id", UUID.randomUUID(), "employeeId", colleague, "minutes", 120, "occasionalReason", "Apoio");
        var activity = Map.of("id", activityId, "description", "Montagem", "serviceId", serviceId,
                "entries", List.of(entry1, entry2));
        var occurrence = Map.of("id", UUID.randomUUID(), "activityId", activityId, "description", "Paralisação", "delayReason", "Chuva");
        String body = json.writeValueAsString(Map.of("operationId", operationId, "activities", List.of(activity),
                "occurrences", List.of(occurrence)));
        String path = diaryPath(first.workId());
        mvc.perform(post(path).with(jwt().jwt(token -> token.subject(first.userId().toString())))
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post(path).with(jwt().jwt(token -> token.subject(first.userId().toString())))
                .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.replay").value(true));
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(first.userId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalMinutes").value(300))
                .andExpect(jsonPath("$.activities[0].entries.length()").value(2))
                .andExpect(jsonPath("$.occurrences[0].delayReason").value("Chuva"));
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(foreign.userId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/company/works/" + first.workId() + "/diary/audit")
                        .with(jwt().jwt(token -> token.subject(first.userId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].actorDisplayName").value("Clara Diário"))
                .andExpect(jsonPath("$[0].actorId").value(first.userId().toString()))
                .andExpect(jsonPath("$[0].actorEmail").doesNotExist());
        mvc.perform(get("/api/v1/company/works/" + first.workId() + "/diary/audit")
                        .with(jwt().jwt(token -> token.subject(foreign.userId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/company/works/" + sameTenantElsewhere + "/diary/audit")
                        .with(jwt().jwt(token -> token.subject(first.userId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(diaryPath(sameTenantElsewhere)).with(jwt().jwt(token -> token.subject(first.userId().toString()))))
                .andExpect(status().isNotFound());

        String transition = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "expectedRevision", 0, "reason", "Conferido"));
        mvc.perform(post(path + "/close").with(jwt().jwt(token -> token.subject(first.userId().toString())))
                .contentType("application/json").content(transition)).andExpect(status().isOk());
        String reopen = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "expectedRevision", 1, "reason", "Ajuste"));
        mvc.perform(post(path + "/reopen").with(jwt().jwt(token -> token.subject(first.userId().toString())))
                .contentType("application/json").content(reopen)).andExpect(status().isOk());
        mvc.perform(get(path + "/revisions/1").with(jwt().jwt(token -> token.subject(first.userId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(true)).andExpect(jsonPath("$.totalMinutes").value(300));
        String staleCorrection = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "expectedRevision", 0,
                "reason", "Ajuste obsoleto", "activities", List.of(activity), "occurrences", List.of(occurrence)));
        mvc.perform(put(path).with(jwt().jwt(token -> token.subject(first.userId().toString())))
                .contentType("application/json").content(staleCorrection)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/company/works/" + first.workId() + "/diary/time-summary")
                .param("from", FIRST_DAY.toString()).param("through", FIRST_DAY.toString())
                .with(jwt().jwt(token -> token.subject(first.userId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_installment WHERE company_id = ?", Integer.class,
                first.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payable WHERE company_id = ?", Integer.class,
                first.companyId())).isZero();
    }

    @Test
    void versionsObservedActivityAndStoppageWithoutCreatingPersonMinutes() throws Exception {
        var actor = fixture();
        var outsider = fixture();
        UUID colleague = createEmployee(actor.companyId());
        grantRolePermissions(actor, "WORKFORCE", "MANAGE_USERS");
        grantRolePermissions(actor, "WORK_DIARY", "READ", "CREATE_DIRECT", "UPDATE_DIRECT");
        grantRolePermissions(outsider, "WORK_DIARY", "READ");
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(), WorkOperation.READ,
                WorkOperation.CREATE, WorkOperation.UPDATE);
        configureZone(actor);
        registerWorkTime(actor, actor.employeeId(), true);
        registerWorkTime(actor, colleague, false);
        var auth = jwt().jwt(token -> token.subject(actor.userId().toString()));
        var activityId = UUID.randomUUID();
        var occurrenceId = UUID.randomUUID();
        var legacyActivity = Map.of("id", activityId, "description", "Montagem", "entries", List.of());
        var activity = new java.util.HashMap<String, Object>(legacyActivity);
        activity.put("timing", Map.of("start", "2026-10-01T09:00:00-03:00"));
        activity.put("participants", List.of(Map.of("employeeId", actor.employeeId()),
                Map.of("employeeId", colleague, "occasionalReason", "Apoio")));
        var plainOccurrence = Map.of("id", occurrenceId, "description", "Chuva observada");
        var occurrence = new java.util.HashMap<String, Object>(plainOccurrence);
        occurrence.put("stoppage", Map.of("start", "2026-10-01T10:00:00-03:00",
                "crewDepartedAt", "2026-10-01T11:15:00-03:00"));
        occurrence.put("timing", Map.of("start", "2026-10-01T09:45:00-03:00", "end", "2026-10-01T11:30:00-03:00"));
        var change = Map.of("operationId", UUID.randomUUID(), "activities", List.of(activity),
                "occurrences", List.of(occurrence), "weatherConditions", List.of("CLOUDY", "RAIN"));
        String path = diaryPath(actor.workId());
        mvc.perform(post(path).with(auth).contentType("application/json").content(json.writeValueAsString(change)))
                .andExpect(status().isNotFound());
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(), WorkOperation.REGISTER_OCCASIONAL);
        mvc.perform(post(path).with(auth).contentType("application/json").content(json.writeValueAsString(change)))
                .andExpect(status().isCreated());
        mvc.perform(post(path).with(auth).contentType("application/json").content(json.writeValueAsString(change)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replay").value(true));
        mvc.perform(get(path).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].participants.length()").value(2))
                .andExpect(jsonPath("$.activities[0].timing.startsAt").value("2026-10-01T12:00:00Z"))
                .andExpect(jsonPath("$.activities[0].timing.endsAt").isEmpty())
                .andExpect(jsonPath("$.totalMinutes").isEmpty())
                .andExpect(jsonPath("$.weatherConditions[1]").value("RAIN"))
                .andExpect(jsonPath("$.occurrences[0].delayReason").isEmpty())
                .andExpect(jsonPath("$.occurrences[0].timing.durationMinutes").value(105))
                .andExpect(jsonPath("$.occurrences[0].stoppage.durationMinutes").value(75))
                .andExpect(jsonPath("$.occurrences[0].stoppage.status").value("CREW_DEPARTED_WITHOUT_RESUMING"));
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(outsider.userId().toString()))))
                .andExpect(status().isNotFound());

        var legacyCorrection = Map.of("operationId", UUID.randomUUID(), "expectedRevision", 0, "reason", "Conferência",
                "activities", List.of(legacyActivity), "occurrences", List.of(plainOccurrence));
        mvc.perform(put(path).with(auth).contentType("application/json").content(json.writeValueAsString(legacyCorrection)))
                .andExpect(status().isOk());
        mvc.perform(get(path).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].participants.length()").value(2))
                .andExpect(jsonPath("$.occurrences[0].stoppage.durationMinutes").value(75))
                .andExpect(jsonPath("$.weatherConditions.length()").value(2));

        var correction = Map.of("operationId", UUID.randomUUID(), "expectedRevision", 1, "reason", "Corrigir a ocorrência",
                "activities", List.of(legacyActivity), "occurrences", List.of(
                        Map.of("id", occurrenceId, "description", "Chuva observada", "clearStoppage", true)));
        mvc.perform(put(path).with(auth).contentType("application/json").content(json.writeValueAsString(correction)))
                .andExpect(status().isOk());
        mvc.perform(get(path).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences[0].stoppage").isEmpty())
                .andExpect(jsonPath("$.occurrences[0].timing.durationMinutes").value(105));
        mvc.perform(get(path + "/revisions/0").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences[0].stoppage.crewDepartedAt").value("2026-10-01T14:15:00Z"));
        var ongoing = Map.of("id", occurrenceId, "description", "Aguardando retomada",
                "timing", Map.of("start", "2026-10-01T09:45:00-03:00"),
                "stoppage", Map.of("start", "2026-10-01T10:00:00-03:00"));
        var ongoingChange = Map.of("operationId", UUID.randomUUID(), "expectedRevision", 2, "reason", "Revisar observação",
                "activities", List.of(legacyActivity), "occurrences", List.of(ongoing));
        mvc.perform(put(path).with(auth).contentType("application/json").content(json.writeValueAsString(ongoingChange)))
                .andExpect(status().isOk());
        mvc.perform(get(path).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences[0].stoppage.status").value("ONGOING"))
                .andExpect(jsonPath("$.occurrences[0].timing.durationMinutes").isEmpty())
                .andExpect(jsonPath("$.occurrences[0].stoppage.durationMinutes").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_time_effective WHERE company_id = ?", Integer.class,
                actor.companyId())).isZero();
    }

    @Test
    void appliesARequestOnlyAfterAReviewerWithTheWorksScopeApprovesIt() throws Exception {
        var requester = fixture();
        var reviewer = new Fixture(requester.companyId(), requester.workId(), createUser(requester.companyId()), requester.employeeId());
        grantRolePermissions(requester, "WORKFORCE", "MANAGE_USERS");
        grantRolePermissions(requester, "WORK_DIARY", "READ", "REQUEST_CREATE");
        grantRolePermissions(reviewer, "WORK_DIARY", "APPROVE", "READ");
        configureZone(requester);
        registerWorkTime(requester, requester.employeeId(), true);
        grantDiaryOperations(requester.companyId(), requester.workId(), requester.userId(), WorkOperation.READ, WorkOperation.CREATE);
        var entry = Map.of("id", UUID.randomUUID(), "employeeId", requester.employeeId(),
                "start", "2026-10-01T09:00:00-03:00", "end", "2026-10-01T10:00:00-03:00");
        var activity = Map.of("id", UUID.randomUUID(), "description", "Revisar", "entries", List.of(entry),
                "timing", Map.of("start", "2026-10-01T09:00:00-03:00", "end", "2026-10-01T11:00:00-03:00"));
        String body = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "activities", List.of(activity),
                "occurrences", List.of(Map.of("id", UUID.randomUUID(), "description", "Retomada",
                        "timing", Map.of("start", "2026-10-01T09:55:00-03:00", "end", "2026-10-01T10:25:00-03:00"),
                        "stoppage", Map.of("start", "2026-10-01T10:00:00-03:00", "resumedAt", "2026-10-01T10:15:00-03:00"))),
                "weatherConditions", List.of("SUNNY")));
        String path = diaryPath(requester.workId());
        String pending = mvc.perform(post(path).with(jwt().jwt(token -> token.subject(requester.userId().toString())))
                .contentType("application/json").content(body)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String approval = "/api/v1/company/change-requests/" + json.readTree(pending).get("changeRequestId").asText() + "/approve";
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(requester.userId().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(post(approval).with(jwt().jwt(token -> token.subject(reviewer.userId().toString())))
                .contentType("application/json").content("{}")).andExpect(status().isNotFound());
        grantDiaryOperations(reviewer.companyId(), reviewer.workId(), reviewer.userId(), WorkOperation.APPROVE);
        mvc.perform(post(approval).with(jwt().jwt(token -> token.subject(reviewer.userId().toString())))
                .contentType("application/json").content("{}")).andExpect(status().isOk());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(requester.userId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalMinutes").value(60))
                .andExpect(jsonPath("$.activities[0].participants[0].employeeId").value(requester.employeeId().toString()))
                .andExpect(jsonPath("$.activities[0].timing.endsAt").value("2026-10-01T14:00:00Z"))
                .andExpect(jsonPath("$.weatherConditions[0]").value("SUNNY"))
                .andExpect(jsonPath("$.occurrences[0].stoppage.status").value("RESUMED"))
                .andExpect(jsonPath("$.occurrences[0].timing.durationMinutes").value(30))
                .andExpect(jsonPath("$.occurrences[0].stoppage.durationMinutes").value(15));
        jdbc.update("UPDATE work_access_grant SET revoked_at = CURRENT_TIMESTAMP WHERE company_id = ? AND user_id = ? AND operation = 'READ'",
                requester.companyId(), requester.userId());
        mvc.perform(get(path + "/revisions/0").with(jwt().jwt(token -> token.subject(requester.userId().toString()))))
                .andExpect(status().isNotFound());

        String nextPath = "/api/v1/company/works/" + requester.workId() + "/diary/" + FIRST_DAY.plusDays(1);
        String nextBody = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "activities", List.of(),
                "occurrences", List.of()));
        String nextPending = mvc.perform(post(nextPath).with(jwt().jwt(token -> token.subject(requester.userId().toString())))
                .contentType("application/json").content(nextBody))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String nextApproval = "/api/v1/company/change-requests/" + json.readTree(nextPending).get("changeRequestId").asText() + "/approve";
        jdbc.update("UPDATE work_access_grant SET revoked_at = CURRENT_TIMESTAMP WHERE company_id = ? AND user_id = ? AND operation = 'CREATE'",
                requester.companyId(), requester.userId());
        mvc.perform(post(nextApproval).with(jwt().jwt(token -> token.subject(reviewer.userId().toString())))
                .contentType("application/json").content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void comparesAnApprovedPlanAndAnExplicitlySelectedDiaryRevision() throws Exception {
        var actor = fixture();
        var reviewer = new Fixture(actor.companyId(), actor.workId(), createUser(actor.companyId()), actor.employeeId());
        grantRolePermissions(actor, "WORKFORCE", "MANAGE_USERS");
        grantRolePermissions(actor, "WORK_DIARY", "READ", "REQUEST_UPDATE", "CREATE_DIRECT");
        grantRolePermissions(reviewer, "WORK_DIARY", "APPROVE");
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(), WorkOperation.READ, WorkOperation.PLAN,
                WorkOperation.CREATE, WorkOperation.CLOSE);
        grantDiaryOperations(reviewer.companyId(), reviewer.workId(), reviewer.userId(), WorkOperation.APPROVE);
        configureZone(actor);
        registerWorkTime(actor, actor.employeeId(), true);
        var auth = jwt().jwt(token -> token.subject(actor.userId().toString()));
        String path = diaryPath(actor.workId());
        String comparison = path + "/comparison";
        UUID milestoneId = UUID.randomUUID();
        String body = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "expectedVersion", 0,
                "reason", "Plano", "schedules", List.of(Map.of("plannedPersonMinutes", 120,
                        "plannedStart", "2026-10-01T08:00:00-03:00", "plannedEnd", "2026-10-01T10:00:00-03:00",
                        "milestones", List.of(Map.of("id", milestoneId, "title", "Conclusão", "dueOn", "2026-09-30"))))));
        String pending = mvc.perform(put(path + "/plan").with(auth).contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mvc.perform(put(path + "/plan").with(auth).contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.replay").value(true));
        mvc.perform(get(comparison).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules.length()").value(0));
        String approval = "/api/v1/company/change-requests/" + json.readTree(pending).get("changeRequestId").asText() + "/approve";
        mvc.perform(post(approval).with(jwt().jwt(token -> token.subject(reviewer.userId().toString())))
                .contentType("application/json").content("{}")).andExpect(status().isOk());
        mvc.perform(get(comparison).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.planVersion").value(1))
                .andExpect(jsonPath("$.schedules[0].time.actualPersonMinutes").value(org.hamcrest.Matchers.nullValue()));

        var entry = Map.of("id", UUID.randomUUID(), "employeeId", actor.employeeId(),
                "start", "2026-10-01T08:30:00-03:00", "end", "2026-10-01T10:00:00-03:00");
        var activity = Map.of("id", UUID.randomUUID(), "description", "Execução geral", "entries", List.of(entry));
        var occurrences = List.of(Map.of("id", UUID.randomUUID(), "description", "Chuva", "delayReason", "Clima"),
                Map.of("id", UUID.randomUUID(), "description", "Espera por material", "delayReason", "Logística"));
        mvc.perform(post(path).with(auth).contentType("application/json").content(json.writeValueAsString(Map.of(
                "operationId", UUID.randomUUID(), "activities", List.of(activity), "occurrences", occurrences))))
                .andExpect(status().isCreated());
        grantRolePermissions(actor, "WORK_DIARY", "UPDATE_DIRECT");
        mvc.perform(post(path + "/close").with(auth).contentType("application/json").content(json.writeValueAsString(Map.of(
                "operationId", UUID.randomUUID(), "expectedRevision", 0, "reason", "Conferido")))).andExpect(status().isOk());
        var observed = Map.of("id", milestoneId, "title", "Conclusão", "dueOn", "2026-09-30",
                "completionDiaryDate", "2026-10-01", "completionDiaryRevision", 1);
        var revised = Map.of("operationId", UUID.randomUUID(), "expectedVersion", 1, "reason", "Conclusão observada",
                "schedules", List.of(Map.of("plannedPersonMinutes", 120, "plannedStart", "2026-10-01T08:00:00-03:00",
                        "plannedEnd", "2026-10-01T10:00:00-03:00", "milestones", List.of(observed))));
        mvc.perform(put(path + "/plan").with(auth).contentType("application/json").content(json.writeValueAsString(revised)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get(comparison).with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules[0].time.startDelayMinutes").value(30))
                .andExpect(jsonPath("$.schedules[0].time.personMinutesDifference").value(-30))
                .andExpect(jsonPath("$.schedules[0].milestones[0].scheduleDelayDays").value(1))
                .andExpect(jsonPath("$.occurrences.length()").value(2));
        mvc.perform(get(comparison).param("planVersion", "1").param("diaryRevision", "0").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planVersion").value(1))
                .andExpect(jsonPath("$.diaryRevision").value(0))
                .andExpect(jsonPath("$.schedules[0].milestones[0].scheduleDelayDays").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get(diaryPath(createWork(actor.companyId())) + "/comparison").with(auth)).andExpect(status().isNotFound());
        var outsider = fixture();
        grantRolePermissions(outsider, "WORK_DIARY", "READ");
        mvc.perform(get(comparison).with(jwt().jwt(token -> token.subject(outsider.userId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void readsTheWorkResultOncePerOriginWithoutExposingUnassignedServiceMargins() throws Exception {
        var actor = fixture();
        var outsider = fixture();
        var secondWork = createWork(actor.companyId());
        grantRolePermissions(actor, "WORKS", "READ");
        grantRolePermissions(actor, "REPORTING", "READ");
        grantRolePermissions(actor, "WORK_DIARY", "READ");
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(), WorkOperation.READ, WorkOperation.READ_FINANCIAL);
        var auth = jwt().jwt(token -> token.subject(actor.userId().toString()));
        String path = "/api/v1/company/works/" + actor.workId() + "/diary/managerial-result";
        mvc.perform(get(path).param("currency", "BRL").param("from", "2026-10-01").param("to", "2026-10-02").with(auth))
                .andExpect(status().isForbidden());

        grantRolePermissions(actor, "BILLING", "READ");
        grantRolePermissions(actor, "PAYABLES", "READ");
        grantRolePermissions(actor, "INVOICES", "READ");
        grantRolePermissions(actor, "RECEIVABLES", "READ");
        UUID service = createService(actor.companyId(), actor.workId());
        UUID contract = jdbc.queryForObject("SELECT contract_id FROM contract_service WHERE id = ?", UUID.class, service);
        UUID installment = billing(actor, contract, service, 1500, "OPEN", "ACTIVE");
        billing(actor, contract, service, 700, "DRAFT", "DRAFT");
        billing(actor, contract, service, 500, "CANCELLED", "CANCELLED");
        UUID operating = payable(actor, actor.workId(), contract, null, "WORK", 600, "BRL");
        payable(actor, actor.workId(), null, null, "WORK", 40, "BRL");
        payable(actor, secondWork, null, null, "WORK", 700, "BRL");
        payable(actor, null, null, null, "COMPANY", 100, "BRL");
        payable(actor, actor.workId(), null, null, "WORK", 900, "USD");
        UUID invoice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO invoice (id, company_id, contract_id, number, issued_on, total_amount, status)
                VALUES (?, ?, ?, 'fin-fixture', DATE '2026-10-01', 1500, 'REGISTERED')
                """, invoice, actor.companyId(), contract);
        UUID tax = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tax_obligation (id, company_id, invoice_id, contract_id, name, due_on, amount, treatment, created_at)
                VALUES (?, ?, ?, ?, 'Independent test charge', DATE '2026-10-31', 80, 'PAYABLE_BY_COMPANY',
                    TIMESTAMPTZ '2026-10-01 12:00:00Z')
                """, tax, actor.companyId(), invoice, contract);
        payable(actor, actor.workId(), contract, tax, "WORK", 80, "BRL");
        UUID receivable = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO receivable (id, company_id, contract_id, billing_installment_id, financial_group_id, due_on, original_amount)
                VALUES (?, ?, ?, ?, ?, DATE '2026-10-31', 1500)
                """, receivable, actor.companyId(), contract, installment, UUID.randomUUID());
        UUID receipt = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO receivable_settlement (id, company_id, receivable_id, contract_id, received_on, amount, payment_method)
                VALUES (?, ?, ?, ?, DATE '2026-10-01', 300, 'PIX')
                """, receipt, actor.companyId(), receivable, contract);
        jdbc.update("""
                INSERT INTO receivable_settlement (id, company_id, receivable_id, contract_id, received_on, amount,
                    payment_method, entry_type, reversal_of_settlement_id)
                VALUES (?, ?, ?, ?, DATE '2026-10-02', -300, 'PIX', 'REVERSAL', ?)
                """, UUID.randomUUID(), actor.companyId(), receivable, contract, receipt);
        UUID allocation = jdbc.queryForObject("SELECT id FROM billing_allocation WHERE billing_installment_id = ?", UUID.class, installment);
        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO invoice_item (id, company_id, invoice_id, contract_id, billing_allocation_id, contract_service_id,
                    allocated_amount, status)
                VALUES (?, ?, ?, ?, ?, ?, 1500, 'REGISTERED')
                """, item, actor.companyId(), invoice, contract, allocation, service);
        UUID exactReceipt = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO receivable_settlement (id, company_id, receivable_id, contract_id, received_on, amount, payment_method)
                VALUES (?, ?, ?, ?, DATE '2026-10-01', 60, 'PIX')
                """, exactReceipt, actor.companyId(), receivable, contract);
        jdbc.update("""
                INSERT INTO invoice_settlement_line (id, company_id, contract_id, receivable_id, settlement_id, invoice_id,
                    invoice_item_id, billing_allocation_id, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 60)
                """, UUID.randomUUID(), actor.companyId(), contract, receivable, exactReceipt, invoice, item, allocation);
        UUID payment = payment(actor, operating, 100, FIRST_DAY, null);
        payment(actor, operating, -100, FIRST_DAY.plusDays(1), payment);
        var dayOne = get(path).param("currency", "BRL").param("from", "2026-10-01").param("to", "2026-10-01");
        mvc.perform(dayOne.with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.projected.result").value("780.00"))
                .andExpect(jsonPath("$.projected.margin").value("52.00"))
                .andExpect(jsonPath("$.projected.tax").value("80.00"))
                .andExpect(jsonPath("$.projected.directExpense").value("40.00"))
                .andExpect(jsonPath("$.recordedCash.result").value("260.00"));
        mvc.perform(get(path).param("currency", "BRL").param("from", "2026-10-02").param("to", "2026-10-02").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordedCash.result").value("-200.00"));
        mvc.perform(get(path).param("contractId", contract.toString()).param("currency", "BRL")
                .param("from", "2026-10-01").param("to", "2026-10-01").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projected.attributedResult").value("820.00"))
                .andExpect(jsonPath("$.recordedCash.result").value("260.00"));
        mvc.perform(get(path).param("contractId", contract.toString()).param("serviceId", service.toString())
                .param("currency", "BRL").param("from", "2026-10-01").param("to", "2026-10-01").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projected.coverage").value("PARTIAL"))
                .andExpect(jsonPath("$.projected.result").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.recordedCash.coverage").value("PARTIAL"))
                .andExpect(jsonPath("$.recordedCash.income").value("60.00"))
                .andExpect(jsonPath("$.effectiveLaborCostCoverage").value("NO_RECONCILED_SOURCE"));
        mvc.perform(get(path).param("currency", "USD").param("from", "2026-10-01").param("to", "2026-10-01").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projected.result").value("-900.00"));
        mvc.perform(get(path).param("currency", "BRL").param("from", "2026-10-01").param("to", "2026-10-02")
                .with(jwt().jwt(token -> token.subject(outsider.userId().toString())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/company/works/" + secondWork + "/diary/managerial-result")
                .param("currency", "BRL").param("from", "2026-10-01").param("to", "2026-10-02").with(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsAdministrableWorkforceRecordsAndOnlyTheUsersEffectiveDiaryOperations() throws Exception {
        var actor = fixture();
        var outsider = fixture();
        UUID secondWork = createWork(actor.companyId());
        grantRolePermissions(actor, "WORKFORCE", "MANAGE_USERS", "MANAGE_ROLES");
        grantRolePermissions(actor, "WORK_DIARY", "READ", "REQUEST_CREATE");
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(),
                WorkOperation.READ, WorkOperation.CREATE, WorkOperation.UPDATE, WorkOperation.READ_FINANCIAL);
        registerWorkTime(actor, actor.employeeId(), true);
        var auth = jwt().jwt(token -> token.subject(actor.userId().toString()));
        String base = "/api/v1/company/workforce";
        mvc.perform(get(base + "/employments").param("employeeId", actor.employeeId().toString()).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(actor.employeeId().toString()));
        mvc.perform(get(base + "/assignments").param("workId", actor.workId().toString())
                .param("from", "2026-10-01").param("through", "2026-10-02").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].employeeName").value("Fixture"))
                .andExpect(jsonPath("$[0].assignment.version").value(0));
        mvc.perform(get(base + "/grants").param("workId", actor.workId().toString())
                .param("userId", actor.userId().toString()).param("limit", "1").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grant.userId").value(actor.userId().toString()));
        mvc.perform(get(base + "/grants").param("workId", outsider.workId().toString()).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/company/works/" + actor.workId() + "/diary/operations").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fieldObservationsSupported").value(true))
                .andExpect(jsonPath("$.operations.length()").value(2))
                .andExpect(jsonPath("$.operations[1].operation").value("CREATE"))
                .andExpect(jsonPath("$.operations[1].mode").value("REQUEST_APPROVAL"));
        mvc.perform(get("/api/v1/company/works/" + secondWork + "/diary/operations").with(auth))
                .andExpect(status().isNotFound());
    }

    private UUID billing(Fixture actor, UUID contract, UUID service, int amount, String status, String allocationStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO billing_installment (id, company_id, contract_id, due_on, status, created_at)
                VALUES (?, ?, ?, DATE '2026-10-31', ?, TIMESTAMPTZ '2026-10-01 12:00:00Z')
                """, id, actor.companyId(), contract, status);
        jdbc.update("""
                INSERT INTO billing_allocation (id, company_id, billing_installment_id, contract_id, contract_service_id,
                    allocated_amount, status, allocation_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'SERVICE')
                """, UUID.randomUUID(), actor.companyId(), id, contract, service, amount, allocationStatus);
        return id;
    }

    private UUID payable(Fixture actor, UUID work, UUID contract, UUID tax, String scope, int amount, String currency) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO payable_financial_group (id, company_id) VALUES (?, ?)", id, actor.companyId());
        jdbc.update("""
                INSERT INTO payable (id, company_id, financial_group_id, financial_scope, work_id, contract_id, tax_obligation_id,
                    reference, due_on, original_amount, currency, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'fixture', DATE '2026-10-31', ?, ?, TIMESTAMPTZ '2026-10-01 12:00:00Z')
                """, id, actor.companyId(), id, scope, work, contract, tax, amount, currency);
        return id;
    }

    private UUID payment(Fixture actor, UUID payable, int amount, LocalDate day, UUID reversal) {
        UUID operation = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payable_settlement_operation (company_id, operation_id, payable_id, fingerprint)
                VALUES (?, ?, ?, repeat('0', 64))
                """, actor.companyId(), operation, payable);
        jdbc.update("""
                INSERT INTO payable_settlement (id, company_id, payable_id, operation_id, reversal_of_id, amount, effective_on,
                    payment_method, recorded_by, recorded_at, justification)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PIX', ?, CURRENT_TIMESTAMP, 'synthetic fixture')
                """, id, actor.companyId(), payable, operation, reversal, amount, day, actor.userId());
        return id;
    }

    @Test
    void concurrentCreationWithDistinctIntentsProducesOnlyOneDiary() throws Exception {
        var actor = fixture();
        grantRolePermissions(actor, "WORKFORCE", "MANAGE_USERS");
        grantRolePermissions(actor, "WORK_DIARY", "CREATE_DIRECT");
        grantDiaryOperations(actor.companyId(), actor.workId(), actor.userId(), WorkOperation.CREATE);
        configureZone(actor);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var requests = List.of(pool.submit(() -> createAtBarrier(actor, barrier)),
                    pool.submit(() -> createAtBarrier(actor, barrier)));
            assertThat(List.of(requests.get(0).get(15, TimeUnit.SECONDS), requests.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_diary WHERE company_id = ? AND work_id = ?",
                Integer.class, actor.companyId(), actor.workId())).isEqualTo(1);
    }

    private int createAtBarrier(Fixture actor, CyclicBarrier barrier) throws Exception {
        String body = json.writeValueAsString(Map.of("operationId", UUID.randomUUID(), "activities", List.of(),
                "occurrences", List.of()));
        barrier.await(10, TimeUnit.SECONDS);
        return mvc.perform(post(diaryPath(actor.workId())).with(jwt().jwt(token -> token.subject(actor.userId().toString())))
                .contentType("application/json").content(body)).andReturn().getResponse().getStatus();
    }
    @Test
    void appliesOnlyAuthorizedWorkforceChangesAndAuditsTheirActor() {
        Fixture first = fixture();
        Fixture second = fixture();
        tenantContext.setCurrentTenant(new TenantContext(first.userId(), first.companyId()));
        try {
            assertThatThrownBy(() -> administration.recordEmployment(first.employeeId(), FIRST_DAY, null))
                    .isInstanceOf(AccessDeniedException.class);
            grantRolePermissions(first, "WORKFORCE", "MANAGE_USERS");
            assertThatThrownBy(() -> administration.recordEmployment(second.employeeId(), FIRST_DAY, null))
                    .isInstanceOf(ApiException.class);
            var employment = administration.recordEmployment(first.employeeId(), FIRST_DAY, null);
            assertThatThrownBy(() -> administration.grantAccess(first.workId(), first.userId(), WorkOperation.CREATE,
                    RECORDED_AT, null)).isInstanceOf(AccessDeniedException.class);
            var assignment = administration.recordAssignment(first.workId(), employment.id(), FIRST_DAY, null);
            assertThatThrownBy(() -> administration.recordAssignment(createWork(first.companyId()), employment.id(),
                    LAST_DAY, null)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> administration.endEmployment(employment.id(), LAST_DAY, employment.version()))
                    .isInstanceOf(ApiException.class);
            administration.endAssignment(assignment.id(), LAST_DAY, assignment.version());
            administration.endEmployment(employment.id(), LAST_DAY, employment.version());
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM audit_event WHERE company_id = ? AND entity_id = ? AND actor_user_id = ?
                    """, Integer.class, first.companyId(), employment.id(), first.userId())).isEqualTo(2);
        } finally {
            tenantContext.clear();
        }
    }

    @Test
    void resolvesTheHistoricalEmploymentAndDistinguishesAnAssignmentFromOccasionalParticipation() {
        Fixture first = fixture();
        Fixture second = fixture();
        UUID otherWork = createWork(first.companyId());
        UUID firstEmployment = UUID.randomUUID();
        workforce.insertEmployment(first.companyId(),
                new EmployeeEmployment(firstEmployment, first.employeeId(), FIRST_DAY, null, 0L),
                first.userId(), RECORDED_AT);
        assertThat(workforce.endEmployment(first.companyId(), firstEmployment, LAST_DAY, 1L)).isFalse();
        assertThat(workforce.endEmployment(first.companyId(), firstEmployment, LAST_DAY, 0L)).isTrue();
        assertThat(workforce.hasEmploymentOverlap(first.companyId(), first.employeeId(), LAST_DAY, null)).isTrue();
        assertThat(workforce.hasEmploymentOverlap(first.companyId(), first.employeeId(), LAST_DAY.plusDays(1), null)).isFalse();
        UUID readmission = UUID.randomUUID();
        workforce.insertEmployment(first.companyId(),
                new EmployeeEmployment(readmission, first.employeeId(), LAST_DAY.plusDays(2), null, 0L),
                first.userId(), RECORDED_AT);

        UUID assignment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO work_assignment (id, company_id, work_id, employee_id, employment_id,
                    started_on, ended_on, recorded_by_user_id, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, assignment, first.companyId(), first.workId(), first.employeeId(), firstEmployment,
                FIRST_DAY, LAST_DAY, first.userId(), RECORDED_AT.atOffset(ZoneOffset.UTC));

        var position = workforce.findPosition(first.companyId(), first.workId(), first.employeeId(), LAST_DAY).orElseThrow();
        assertThat(position.employmentId()).isEqualTo(firstEmployment);
        assertThat(position.assignmentId()).isEqualTo(assignment);
        var occasional = workforce.findPosition(first.companyId(), otherWork, first.employeeId(), LAST_DAY).orElseThrow();
        assertThat(occasional.isOccasional()).isTrue();
        assertThat(occasional.assignedToOtherWork()).isTrue();
        assertThat(workforce.findPosition(first.companyId(), first.workId(), first.employeeId(), LAST_DAY.plusDays(1))).isEmpty();
        assertThat(workforce.findPosition(first.companyId(), first.workId(), first.employeeId(), LAST_DAY.plusDays(2)))
                .hasValueSatisfying(current -> assertThat(current.employmentId()).isEqualTo(readmission));
        assertThat(workforce.findPosition(first.companyId(), second.workId(), first.employeeId(), LAST_DAY)).isEmpty();
        assertThat(workforce.findEmployment(second.companyId(), firstEmployment)).isEmpty();
        assertThat(workforce.hasAssignmentAfter(first.companyId(), firstEmployment, LAST_DAY.minusDays(1))).isTrue();
    }

    @Test
    void checksGrantOperationWorkTenantExpiryAndRevocation() {
        Fixture first = fixture();
        Fixture second = fixture();
        UUID otherWork = createWork(first.companyId());
        String insertGrant = """
                INSERT INTO work_access_grant (id, company_id, work_id, user_id, operation,
                    starts_at, ends_at, granted_by_user_id, granted_at)
                VALUES (?, ?, ?, ?, 'CREATE', ?, ?, ?, ?)
                """;
        var startsAt = RECORDED_AT.atOffset(ZoneOffset.UTC);
        var endsAt = startsAt.plusSeconds(60);
        UUID grantId = UUID.randomUUID();
        jdbc.update(insertGrant, grantId, first.companyId(), first.workId(), first.userId(),
                startsAt, endsAt, first.userId(), startsAt);

        assertThat(workforce.hasGrant(first.companyId(), first.workId(), first.userId(), WorkOperation.CREATE, RECORDED_AT)).isTrue();
        assertThat(workforce.hasGrant(first.companyId(), otherWork, first.userId(), WorkOperation.CREATE, RECORDED_AT)).isFalse();
        assertThat(workforce.hasGrant(second.companyId(), first.workId(), first.userId(), WorkOperation.CREATE, RECORDED_AT)).isFalse();
        assertThat(workforce.hasGrant(first.companyId(), first.workId(), first.userId(), WorkOperation.READ, RECORDED_AT)).isFalse();
        assertThat(workforce.hasGrant(first.companyId(), first.workId(), first.userId(), WorkOperation.CREATE, endsAt.toInstant())).isFalse();
        assertThatThrownBy(() -> jdbc.update(insertGrant, UUID.randomUUID(), first.companyId(), first.workId(),
                second.userId(), startsAt, endsAt, first.userId(), startsAt))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("UPDATE work_access_grant SET revoked_at = ? WHERE id = ?", startsAt.plusSeconds(30), grantId);
        assertThat(workforce.hasGrant(first.companyId(), first.workId(), first.userId(), WorkOperation.CREATE,
                RECORDED_AT.plusSeconds(31))).isFalse();
    }

    private Fixture fixture() {
        UUID companyId = UUID.randomUUID();
        String document = companyId.toString().substring(0, 12);
        jdbc.update("INSERT INTO company (id, corporate_name, document, email) VALUES (?, 'Fixture', ?, ?)",
                companyId, document, companyId + "@example.test");
        return new Fixture(companyId, createWork(companyId), createUser(companyId), createEmployee(companyId));
    }

    private UUID createUser(UUID companyId) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, email, user_type, status, password_hash, password_changed_at)
                VALUES (?, ?, 'COMPANY', 'ACTIVE', 'synthetic-test-fixture', CURRENT_TIMESTAMP)
                """,
                userId, userId + "@example.test");
        jdbc.update("INSERT INTO company_user (id, company_id, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), companyId, userId);
        return userId;
    }

    private UUID createEmployee(UUID companyId) {
        UUID employeeId = UUID.randomUUID();
        jdbc.update("INSERT INTO employee (id, company_id, full_name) VALUES (?, ?, 'Fixture')", employeeId, companyId);
        return employeeId;
    }

    private void grantRolePermissions(Fixture fixture, String module, String... actions) {
        UUID roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO access_role (id, company_id, name) VALUES (?, ?, ?)",
                roleId, fixture.companyId(), roleId.toString());
        jdbc.update("INSERT INTO user_role (id, user_id, role_id, company_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), fixture.userId(), roleId, fixture.companyId());
        for (String action : actions) {
            jdbc.update("""
                    INSERT INTO role_permission (id, role_id, permission_id)
                    SELECT ?, ?, id FROM permission WHERE module = ? AND action = ?
                    """, UUID.randomUUID(), roleId, module, action);
        }
    }

    private void grantDiaryOperations(UUID companyId, UUID workId, UUID userId, WorkOperation... operations) {
        for (WorkOperation operation : operations) {
            jdbc.update("""
                    INSERT INTO work_access_grant (id, company_id, work_id, user_id, operation, starts_at,
                        granted_by_user_id, granted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), companyId, workId, userId, operation.name(),
                    RECORDED_AT.atOffset(ZoneOffset.UTC), userId, RECORDED_AT.atOffset(ZoneOffset.UTC));
        }
    }

    private void configureZone(Fixture fixture) throws Exception {
        mvc.perform(put("/api/v1/company/works/" + fixture.workId() + "/diary/time-zone")
                .with(jwt().jwt(token -> token.subject(fixture.userId().toString())))
                .contentType("application/json").content("{\"timeZone\":\"America/Sao_Paulo\"}"))
                .andExpect(status().isOk());
    }

    private void registerWorkTime(Fixture fixture, UUID employee, boolean assigned) {
        tenantContext.setCurrentTenant(new TenantContext(fixture.userId(), fixture.companyId()));
        try {
            var employment = administration.recordEmployment(employee, FIRST_DAY, null);
            if (assigned) {
                administration.recordAssignment(fixture.workId(), employment.id(), FIRST_DAY, null);
            }
        } finally {
            tenantContext.clear();
        }
    }

    private static String diaryPath(UUID workId) {
        return "/api/v1/company/works/" + workId + "/diary/" + FIRST_DAY;
    }

    private UUID createService(UUID companyId, UUID workId) {
        UUID contractId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contract (id, company_id, work_id, name, status, currency)
                VALUES (?, ?, ?, 'Contrato', 'ACTIVE', 'BRL')
                """, contractId, companyId, workId);
        jdbc.update("INSERT INTO contract_service (id, company_id, contract_id, name) VALUES (?, ?, ?, 'Serviço')",
                serviceId, companyId, contractId);
        return serviceId;
    }

    private UUID createWork(UUID companyId) {
        UUID customerId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        String document = "%014d".formatted(Math.floorMod(customerId.getLeastSignificantBits(), 100_000_000_000_000L));
        jdbc.update("""
                INSERT INTO final_customer (id, company_id, customer_type, name, document)
                VALUES (?, ?, 'LEGAL', 'Fixture', ?)
                """, customerId, companyId, document);
        jdbc.update("""
                INSERT INTO obra (id, company_id, final_customer_id, name, status, execution_location_type)
                VALUES (?, ?, ?, 'Fixture', 'ACTIVE', 'FINAL_CUSTOMER_LOCATION')
                """, workId, companyId, customerId);
        return workId;
    }

    private record Fixture(UUID companyId, UUID workId, UUID userId, UUID employeeId) {
    }
}
