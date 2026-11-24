package com.renovar.canteiro.io.receivables;

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
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.security.MessageDigest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@AutoConfigureMockMvc
class InvoiceAttachmentApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private InvoiceRepository invoices;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository companyUsers;
    @Autowired private PasswordHasher hasher;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private AuditEventRepository audits;
    @Autowired private InvoiceAttachmentRepository attachments;
    @MockitoBean private StorageProvider storage;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void returnsCompleteImmediateUploadMetadataWithThePersistedCreationTimestamp() throws Exception {
        var f = fixture(AccessAction.CREATE_DIRECT);
        byte[] pdf = validPdf(1);
        var response = mvc.perform(multipart("/api/v1/company/invoices/{invoiceId}/attachments", f.invoiceId())
                .file(new MockMultipartFile("file", "diagnostic.pdf", "application/pdf", pdf))
                .param("attachmentType", "PDF").with(jwt().jwt(token -> token.subject(f.userId().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse();
        var metadata = json.readTree(response.getContentAsString());
        assertEquals(8, metadata.size());
        assertTrue(metadata.has("createdAt"));
        assertTrue(metadata.path("createdAt").isString());
        Instant returnedCreatedAt = Instant.parse(metadata.path("createdAt").asText());
        assertEquals(1, attachmentCount(f));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "select created_at is not null from invoice_attachment where id=? and company_id=?", Boolean.class,
                UUID.fromString(metadata.path("id").asText()), f.companyId()));
        assertEquals(returnedCreatedAt, jdbc.queryForObject(
                "select created_at from invoice_attachment where id=? and company_id=?",
                (rs, row) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant(),
                UUID.fromString(metadata.path("id").asText()), f.companyId()));
    }

    @Test
    void listsMetadataWithStablePaginationAndWithoutLoadingStorage() throws Exception {
        var f = fixture(AccessAction.READ);
        for (int n = 1; n <= 3; n++) stored(f, InvoiceAttachmentType.XML, "nota-" + n + ".xml", ("<nfeProc>" + "a".repeat(n) + "</nfeProc>").getBytes(StandardCharsets.UTF_8));
        jdbc.update("update invoice_attachment set created_at=TIMESTAMPTZ '2026-10-25 15:00:00+00' where invoice_id=?", f.invoiceId());
        var all = list(f, get(attachmentsPath(f)), 200);
        assertEquals(3, all.path("totalItems").asInt());
        assertTrue(all.at("/items/0/id").asText().compareTo(all.at("/items/1/id").asText()) < 0);
        for (int page = 0; page < 3; page++) {
            var result = list(f, get(attachmentsPath(f)).param("size", "1").param("page", Integer.toString(page)), 200);
            assertEquals(all.path("items").get(page), result.at("/items/0")); assertEquals(3, result.path("totalPages").asInt());
        }
        assertEquals(0, list(f, get(attachmentsPath(f)).param("page", "3").param("size", "1"), 200).path("items").size());
        assertEquals("nota-3.xml", list(f, get(attachmentsPath(f)).param("sort", "contentSize,desc"), 200).at("/items/0/originalFilename").asText());
        for (String field : List.of("storageKey", "storagePath", "path", "url", "accessKey", "content", "uploadedByUserId")) assertFalse(all.at("/items/0").has(field));
        assertFalse(all.toString().contains("private-test-storage"));
        assertEquals(64, all.at("/items/0/sha256").asText().length());
        assertTrue(audits.findByCompanyId(f.companyId(), Pageable.unpaged()).isEmpty());
        verifyNoInteractions(storage);
    }

    @Test
    void metadataValidatesPageAndSortAndDistinguishesEmptyFromMissingInvoice() throws Exception {
        var f = fixture(AccessAction.READ);
        assertEquals(0, list(f, get(attachmentsPath(f)), 200).path("totalItems").asInt());
        for (String[] invalid : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"},
                {"sort", "storageKey,asc"}, {"sort", "createdAt,invalid"}, {"sort", "createdAt"}}) {
            list(f, get(attachmentsPath(f)).param(invalid[0], invalid[1]), 400);
        }
        list(f, get("/api/v1/company/invoices/invalid/attachments"), 400);
        list(f, get("/api/v1/company/invoices/" + UUID.randomUUID() + "/attachments"), 404);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnauthenticatedAndRequiresReadForListingAndReadPlusExportForDownload() throws Exception {
        for (String path : List.of("/api/v1/company/invoices/" + UUID.randomUUID() + "/attachments",
                "/api/v1/company/invoices/" + UUID.randomUUID() + "/attachments/" + UUID.randomUUID() + "/download")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-test-attachment-token"))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        var creator = fixture(AccessAction.CREATE_DIRECT);
        list(creator, get(attachmentsPath(creator)), 403);
        list(creator, get(attachmentsPath(creator) + "/" + UUID.randomUUID() + "/download"), 403);
        var reader = fixture(AccessAction.READ);
        list(reader, get(attachmentsPath(reader) + "/" + UUID.randomUUID() + "/download"), 403);
        var exporter = fixture(AccessAction.EXPORT);
        list(exporter, get(attachmentsPath(exporter)), 403);
        list(exporter, get(attachmentsPath(exporter) + "/" + UUID.randomUUID() + "/download"), 403);
        verifyNoInteractions(storage);
    }

    @Test
    void isolatesTenantAndExactInvoiceAttachmentChainBeforeAccessingStorage() throws Exception {
        var owner = fixture(AccessAction.READ, AccessAction.EXPORT);
        var other = fixture(AccessAction.READ, AccessAction.EXPORT);
        var attachment = stored(owner, InvoiceAttachmentType.XML, "nota.xml", "<nfeProc/>".getBytes(StandardCharsets.UTF_8));
        list(other, get(attachmentsPath(owner)), 404);
        list(other, get(attachmentsPath(owner) + "/" + attachment.getId() + "/download"), 404);
        list(other, get(attachmentsPath(other) + "/" + attachment.getId() + "/download"), 404);
        var second = invoices.save(Invoice.register(owner.companyId(), invoices.findByIdAndCompanyId(owner.invoiceId(), owner.companyId()).orElseThrow().getContractId(),
                "NF-SECOND", null, null, LocalDate.of(2026, 10, 25), new BigDecimal("1.00")));
        list(owner, get("/api/v1/company/invoices/" + second.getId() + "/attachments/" + attachment.getId() + "/download"), 404);
        list(owner, get(attachmentsPath(owner) + "/" + UUID.randomUUID() + "/download"), 404);
        list(owner, get(attachmentsPath(owner) + "/invalid/download"), 400);
        assertEquals(0, list(other, get(attachmentsPath(other)).param("companyId", owner.companyId().toString()), 200).path("totalItems").asInt());
        verifyNoInteractions(storage);
        assertTrue(audits.findByCompanyId(owner.companyId(), Pageable.unpaged()).isEmpty());
    }

    @Test
    void downloadsExactPdfAndXmlWithSafeHeadersAndAuditIncludingCancelledInvoice() throws Exception {
        var f = fixture(AccessAction.READ, AccessAction.EXPORT);
        var invoice = invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow();
        invoices.updateStatus(invoice.cancel());
        var financialBefore = financialSnapshot(f);
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf = output.toByteArray();
        }
        for (var type : List.of(InvoiceAttachmentType.PDF, InvoiceAttachmentType.XML)) {
            byte[] bytes = type == InvoiceAttachmentType.PDF ? pdf : "<nfeProc><NFe>synthetic-private-content</NFe></nfeProc>".getBytes(StandardCharsets.UTF_8);
            var attachment = stored(f, type, "C:\\pasta\\Nota Ação\r\nSet-Cookie: erro.exe", bytes);
            when(storage.load(attachment.getStorageKey())).thenReturn(bytes);
            var response = mvc.perform(get(attachmentsPath(f) + "/" + attachment.getId() + "/download")
                    .with(jwt().jwt(token -> token.subject(f.userId().toString()))))
                    .andExpect(status().isOk()).andExpect(content().contentType(type == InvoiceAttachmentType.PDF ? "application/pdf" : "application/xml"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, Integer.toString(bytes.length)))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION)).andReturn().getResponse();
            assertArrayEquals(bytes, response.getContentAsByteArray());
            var disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
            assertEquals("attachment", disposition.getType());
            assertEquals("nota-acao-set-cookie-erro." + (type == InvoiceAttachmentType.PDF ? "pdf" : "xml"), disposition.getFilename());
            verify(storage).load(attachment.getStorageKey());
        }
        var metadata = list(f, get(attachmentsPath(f)), 200);
        assertEquals(2, metadata.path("totalItems").asInt());
        assertFalse(metadata.toString().contains("pasta"));
        assertFalse(metadata.at("/items/0/originalFilename").asText().contains("\r"));
        assertFalse(metadata.at("/items/0/originalFilename").asText().contains("\n"));
        assertEquals(financialBefore, financialSnapshot(f));
        var events = audits.findByCompanyId(f.companyId(), Pageable.unpaged()).getContent();
        assertEquals(2, events.size());
        for (var event : events) {
            assertEquals("EXPORT", event.getAction().name()); assertEquals(f.userId(), event.getActorUserId());
            assertEquals("InvoiceAttachment", event.getEntityType()); assertNull(event.getBeforeData()); assertNull(event.getAfterData());
            assertEquals("invoice_attachment_download", event.getMetadata().values().get("origin"));
            String data = event.getMetadata().values().toString();
            for (String secret : List.of("private-test-storage", "synthetic-private-content", "Set-Cookie", "storageKey", "sha256")) assertFalse(data.contains(secret));
        }
    }

    @Test
    void unavailableOrCorruptedContentFailsSafelyWithoutSuccessfulAuditOrFinancialChanges() throws Exception {
        var f = fixture(AccessAction.READ, AccessAction.EXPORT);
        var attachment = stored(f, InvoiceAttachmentType.XML, "nota.xml", "<nfeProc/>".getBytes(StandardCharsets.UTF_8));
        var financialBefore = financialSnapshot(f);
        when(storage.load(attachment.getStorageKey())).thenThrow(new IllegalStateException("private-test-storage/path-failure"));
        var error = list(f, get(attachmentsPath(f) + "/" + attachment.getId() + "/download"), 503);
        assertFalse(error.toString().contains("private-test-storage"));
        doReturn("<nfeProcX>".getBytes(StandardCharsets.UTF_8)).when(storage).load(attachment.getStorageKey());
        list(f, get(attachmentsPath(f) + "/" + attachment.getId() + "/download"), 503);
        doReturn(new byte[0]).when(storage).load(attachment.getStorageKey());
        list(f, get(attachmentsPath(f) + "/" + attachment.getId() + "/download"), 503);
        assertTrue(audits.findByCompanyId(f.companyId(), Pageable.unpaged()).isEmpty());
        assertEquals(financialBefore, financialSnapshot(f));
    }

    private List<Map<String, Object>> financialSnapshot(Fixture f) {
        return jdbc.queryForList("""
                select 'invoice' kind,id::text,to_jsonb(i)::text data from invoice i where company_id=?
                union all select 'item',id::text,to_jsonb(i)::text from invoice_item i where company_id=?
                union all select 'allocation',id::text,to_jsonb(a)::text from billing_allocation a where company_id=?
                union all select 'receivable',id::text,to_jsonb(r)::text from receivable r where company_id=?
                union all select 'settlement',id::text,to_jsonb(s)::text from receivable_settlement s where company_id=? order by 1,2
                """, f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId());
    }

    private InvoiceAttachment stored(Fixture f, InvoiceAttachmentType type, String filename, byte[] bytes) throws Exception {
        var invoice = invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow();
        return attachments.save(InvoiceAttachment.create(f.companyId(), f.invoiceId(), invoice.getContractId(), type, filename,
                type == InvoiceAttachmentType.PDF ? "application/pdf" : "application/xml", bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "private-test-storage/" + UUID.randomUUID(), f.userId()));
    }

    private String attachmentsPath(Fixture f) { return "/api/v1/company/invoices/" + f.invoiceId() + "/attachments"; }

    private JsonNode list(Fixture f, MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request.with(jwt().jwt(token -> token.subject(f.userId().toString())))).andExpect(status().is(expected));
        if (expected == 200) result.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        if (expected >= 400) result.andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void acceptsOnePdfAlongsideLegacyXmlAndPersistsOnlySafeAuditMetadata() throws Exception {
        var fixture = fixture(AccessAction.CREATE_DIRECT);
        byte[] xml = "<nfeProc><NFe><infNFe>private-fixture-content</infNFe></NFe></nfeProc>".getBytes(StandardCharsets.UTF_8);
        stored(fixture, InvoiceAttachmentType.XML, "legado.xml", xml);
        upload(fixture, fixture.invoiceId(), "PDF", "nota.pdf", "application/pdf", validPdf(1), 200);
        upload(fixture, fixture.invoiceId(), "PDF", "outra.pdf", "application/pdf", validPdf(2), 409)
                .andExpect(jsonPath("$.reason").value("INVOICE_PDF_ALREADY_ATTACHED"));
        assertEquals(2, attachmentCount(fixture));
        var events = audits.findByCompanyId(fixture.companyId(), Pageable.unpaged()).getContent();
        assertEquals(1, events.size());
        for (var event : events) {
            assertEquals("InvoiceAttachment", event.getEntityType());
            assertNull(event.getBeforeData());
            assertFalse(event.getAfterData().values().toString().contains("private-fixture-content"));
            assertFalse(event.getMetadata().values().toString().contains("private-fixture-content"));
        }
        verify(storage, times(1)).store(any(), any());
    }

    @Test
    void rejectsNonPdfMediaAndInvalidPdfWithoutStorageOrAudit() throws Exception {
        var fixture = fixture(AccessAction.CREATE_DIRECT);
        upload(fixture, fixture.invoiceId(), "PDF", "nota.pdf", "application/pdf", "not-pdf".getBytes(StandardCharsets.UTF_8), 400);
        upload(fixture, fixture.invoiceId(), "XML", "nota.xml", "application/xml", "<root/>".getBytes(StandardCharsets.UTF_8), 415)
                .andExpect(jsonPath("$.reason").value("INVOICE_ATTACHMENT_PDF_REQUIRED"));
        upload(fixture, fixture.invoiceId(), "PDF", "nota.pdf", "application/octet-stream", validPdf(1), 415)
                .andExpect(jsonPath("$.reason").value("INVOICE_ATTACHMENT_PDF_REQUIRED"));
        assertEquals(0, attachmentCount(fixture));
        assertTrue(audits.findByCompanyId(fixture.companyId(), Pageable.unpaged()).isEmpty());
        verifyNoInteractions(storage);
    }

    @Test
    void preservesDuplicateLegacyPdfsAndRejectsNewUploads() throws Exception {
        var fixture = fixture(AccessAction.CREATE_DIRECT, AccessAction.READ);
        stored(fixture, InvoiceAttachmentType.PDF, "legado-a.pdf", validPdf(1));
        stored(fixture, InvoiceAttachmentType.PDF, "legado-b.pdf", validPdf(2));
        upload(fixture, fixture.invoiceId(), "PDF", "nova.pdf", "application/pdf", validPdf(3), 409)
                .andExpect(jsonPath("$.reason").value("INVOICE_PDF_ALREADY_ATTACHED"));
        assertEquals(2, list(fixture, get(attachmentsPath(fixture)), 200).path("totalItems").asInt());
        assertEquals(2, attachmentCount(fixture));
        verifyNoInteractions(storage);
    }

    @Test
    void serializesConcurrentUploadsOnTheInvoiceAndStoresExactlyOnePdf() throws Exception {
        var fixture = fixture(AccessAction.CREATE_DIRECT);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> concurrentUpload(fixture, start, "primeira.pdf", validPdf(1)));
            var second = workers.submit(() -> concurrentUpload(fixture, start, "segunda.pdf", validPdf(2)));
            var statuses = List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        }
        assertEquals(1, attachmentCount(fixture));
        assertEquals(1, audits.findByCompanyId(fixture.companyId(), Pageable.unpaged()).getTotalElements());
        verify(storage, times(1)).store(any(), any());
    }

    private int concurrentUpload(Fixture fixture, java.util.concurrent.CyclicBarrier start, String filename, byte[] pdf) throws Exception {
        start.await(10, java.util.concurrent.TimeUnit.SECONDS);
        return mvc.perform(multipart(attachmentsPath(fixture))
                .file(new MockMultipartFile("file", filename, "application/pdf", pdf)).param("attachmentType", "PDF")
                .with(jwt().jwt(token -> token.subject(fixture.userId().toString())))).andReturn().getResponse().getStatus();
    }

    private byte[] validPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void requiresDirectAuthorityAndDoesNotExposeAnotherTenantsInvoice() throws Exception {
        var owner = fixture(AccessAction.CREATE_DIRECT);
        var other = fixture(AccessAction.CREATE_DIRECT);
        var reader = fixture(AccessAction.READ);
        var requester = fixture(AccessAction.REQUEST_CREATE);
        byte[] xml = "<nfeProc/>".getBytes(StandardCharsets.UTF_8);
        upload(other, owner.invoiceId(), "XML", "nota.xml", "application/xml", xml, 404);
        upload(reader, reader.invoiceId(), "XML", "nota.xml", "application/xml", xml, 403);
        upload(requester, requester.invoiceId(), "XML", "nota.xml", "application/xml", xml, 403);
        verifyNoInteractions(storage);
        assertEquals(0, attachmentCount(owner));
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            Fixture fixture, UUID invoiceId, String type, String filename, String mime, byte[] bytes, int status) throws Exception {
        var result = mvc.perform(multipart("/api/v1/company/invoices/{invoiceId}/attachments", invoiceId)
                .file(new MockMultipartFile("file", filename, mime, bytes)).param("attachmentType", type)
                .with(jwt().jwt(t -> t.subject(fixture.userId().toString()))));
        result.andExpect(status().is(status));
        if (status == 200) {
            result.andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                    .andExpect(jsonPath("$.attachmentType").value(type))
                    .andExpect(jsonPath("$.originalFilename").value(filename))
                    .andExpect(jsonPath("$.contentType").value(mime))
                    .andExpect(jsonPath("$.contentSize").value(bytes.length))
                    .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }
        if (status >= 400) {
            result.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-test-path"))));
        }
        return result;
    }

    private Fixture fixture(AccessAction... actions) {
        String suffix = UUID.randomUUID().toString();
        Company company = companies.save(Company.create("Fixture company", null,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.test", null, null, null));
        FinalCustomer customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Fixture customer", "12345678000195"));
        Work work = works.save(Work.create(company.getId(), customer.getId(), "Fixture work", null,
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        Contract contract = contracts.save(Contract.create(company.getId(), work.getId(), null, "Fixture contract", ContractStatus.ACTIVE, null, null, null));
        Invoice invoice = invoices.save(Invoice.register(company.getId(), contract.getId(), "NF-TEST", null, null, LocalDate.of(2026, 10, 22), new BigDecimal("1.00")));
        User user = User.create(suffix + "@example.test", UserType.COMPANY);
        user.activate(hasher.hash("Canteiro#2026Seguro"), Instant.now()); user = users.save(user);
        companyUsers.save(CompanyUser.create(user.getId(), company.getId()));
        Role role = roles.save(Role.create(company.getId(), "Fixture role", null));
        for (AccessAction action : actions) {
            Permission permission = permissions.findByModuleAndAction(AccessModule.INVOICES, action).orElseThrow();
            rolePermissions.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoles.save(UserRole.create(user.getId(), role.getId(), company.getId()));
        return new Fixture(company.getId(), user.getId(), invoice.getId());
    }

    private int attachmentCount(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from invoice_attachment where invoice_id = ? and company_id = ?",
                Integer.class, fixture.invoiceId(), fixture.companyId());
    }

    private record Fixture(UUID companyId, UUID userId, UUID invoiceId) { }
}
