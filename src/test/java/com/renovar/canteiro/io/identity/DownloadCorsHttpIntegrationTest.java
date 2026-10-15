package com.renovar.canteiro.io.identity;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.identity.application.AccessTokenIssuer;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ContentDisposition;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real HTTP/Tomcat checks: MockMvc alone does not prove browser-visible download headers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "security.cors.allowed-origins=http://localhost:5173,https://frontend.test")
class DownloadCorsHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final List<String> ALLOWED_ORIGINS = List.of("http://localhost:5173", "https://frontend.test");
    private static final String OTHER_ORIGIN = "https://untrusted.example";

    @LocalServerPort private int port;
    @Autowired private CompanyRepository companies;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository memberships;
    @Autowired private PasswordHasher passwords;
    @Autowired private AccessTokenIssuer tokens;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private MeasurementRepository measurements;
    @Autowired private MeasurementVersionRepository versions;
    @Autowired private MeasurementItemRepository items;
    @Autowired private MeasurementContinuityRepository continuity;
    @Autowired private PlatformTransactionManager transactions;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private Company company;
    private Measurement measurement;
    private MeasurementVersion version;
    private String exporterToken;

    @BeforeEach
    void createIsolatedDownloadFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> persistDownloadFixture());
    }

    private void persistDownloadFixture() {
        company = company();
        exporterToken = userToken(company, true);
        measurement = measurements.save(Measurement.create(company.getId(), null, null,
                "Medição CORS", "CORS-TEST", null, LocalDate.of(2026, 10, 15)));
        version = versions.save(MeasurementVersion.create(company.getId(), measurement.getId(), 1));
        Instant now = Instant.now();
        var service = continuity.save(new MeasurementContinuousService(null, company.getId(),
                measurement.getId(), "Serviço CORS", 1, null, null, 0, now, now));
        var item = items.save(MeasurementItem.createSquareMeter(company.getId(), version.getId(), 1,
                service.serviceTitle(), "Atividade", null, LocalDate.of(2026, 10, 15),
                new BigDecimal("2.0000"), new BigDecimal("25.00")));
        continuity.bindItem(company.getId(), measurement.getId(), version.getId(), item.getId(), service.id());
    }

    @Test
    void exposesServerFilenameAndCorrelationForAllSixDownloadsOnlyToConfiguredOrigins() throws Exception {
        for (String origin : ALLOWED_ORIGINS) {
            for (String suffix : reportSuffixes()) {
                var response = get(path(suffix), origin, exporterToken);
                assertEquals(200, response.statusCode(), suffix);
                assertCors(response, origin);
                assertEquals("cors-download-test", header(response, "X-Correlation-Id"));
                String disposition = header(response, "Content-Disposition");
                assertTrue(disposition.startsWith(suffix.endsWith("preview") ? "inline;" : "attachment;"));
                String filename = ContentDisposition.parse(disposition).getFilename();
                assertTrue(filename != null && filename.toLowerCase(Locale.ROOT).contains("medicao-cors"), filename);
                assertTrue(filename.endsWith(suffix.endsWith("spreadsheet") ? ".xlsx" : ".pdf"), filename);
                assertEquals("no-store", header(response, "Cache-Control"));
                assertTrue(response.body().length > 4);
                assertTrue(header(response, "Content-Type").contains(suffix.endsWith("spreadsheet")
                        ? "spreadsheetml.sheet" : "application/pdf"));
            }
        }
    }

    @Test
    void acceptsPreflightWithAuthorizationOnlyForConfiguredOrigins() throws Exception {
        for (String origin : ALLOWED_ORIGINS) {
            var response = preflight(origin, "GET", "authorization,x-correlation-id");
            assertEquals(200, response.statusCode());
            assertCors(response, origin);
            assertTrue(header(response, "Access-Control-Allow-Methods").contains("GET"));
            assertTrue(header(response, "Access-Control-Allow-Headers").toLowerCase(Locale.ROOT).contains("authorization"));
            assertFalse(header(response, "Access-Control-Allow-Methods").contains("*"));
        }
    }

    @Test
    void rejectsUntrustedOriginForAuthenticatedGetAndPreflight() throws Exception {
        for (var response : List.of(get(path("/accumulated/pdf"), OTHER_ORIGIN, exporterToken),
                preflight(OTHER_ORIGIN, "GET", "authorization"))) {
            assertEquals(403, response.statusCode());
            assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").isEmpty());
        }
    }

    @Test
    void rejectsUnapprovedPreflightMethodOrHeader() throws Exception {
        for (var response : List.of(preflight(ALLOWED_ORIGINS.getFirst(), "TRACE", "authorization"),
                preflight(ALLOWED_ORIGINS.getFirst(), "GET", "x-unapproved-header"))) {
            assertEquals(403, response.statusCode());
            assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        }
    }

    @Test
    void preservesExportPermissionAndTenantIsolationWithCors() throws Exception {
        var forbidden = get(path("/accumulated/pdf"), ALLOWED_ORIGINS.getFirst(), userToken(company, false));
        assertEquals(403, forbidden.statusCode());
        assertCors(forbidden, ALLOWED_ORIGINS.getFirst());
        assertTrue(header(forbidden, "Content-Type").contains("application/problem+json"));
        var notFound = get(path("/accumulated/pdf"), ALLOWED_ORIGINS.getFirst(), userToken(company(), true));
        assertEquals(404, notFound.statusCode());
        assertCors(notFound, ALLOWED_ORIGINS.getFirst());
        assertTrue(header(notFound, "Content-Type").contains("application/problem+json"));
    }

    @Test
    void sameOriginDownloadKeepsFilenameWithoutAdvertisingCors() throws Exception {
        var response = get(path("/accumulated/pdf"), null, exporterToken);
        assertEquals(200, response.statusCode());
        assertTrue(header(response, "Content-Disposition").startsWith("attachment;"));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    private void assertCors(HttpResponse<byte[]> response, String origin) {
        assertEquals(origin, header(response, "Access-Control-Allow-Origin"));
        assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
        var exposed = Arrays.stream(header(response, "Access-Control-Expose-Headers").split(","))
                .map(String::trim).map(name -> name.toLowerCase(Locale.ROOT)).toList();
        assertEquals(2, exposed.size());
        assertTrue(exposed.containsAll(List.of("content-disposition", "x-correlation-id")));
        assertFalse(exposed.contains("*"));
    }

    private HttpResponse<byte[]> get(String path, String origin, String token) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30)).GET()
                .header("Authorization", "Bearer " + token).header("X-Correlation-Id", "cors-download-test");
        if (origin != null) builder.header("Origin", origin);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> preflight(String origin, String method, String headers) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path("/accumulated/pdf"))).timeout(Duration.ofSeconds(15))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", origin)
                .header("Access-Control-Request-Method", method).header("Access-Control-Request-Headers", headers)
                .build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String path(String suffix) {
        return "/api/v1/company/measurements/" + measurement.getId() + suffix;
    }

    private List<String> reportSuffixes() {
        String versionPath = "/versions/" + version.getId();
        return List.of("/accumulated/pdf", "/accumulated/spreadsheet", "/accumulated/spreadsheet/preview",
                versionPath + "/pdf", versionPath + "/spreadsheet", versionPath + "/spreadsheet/preview");
    }

    private Company company() {
        String suffix = UUID.randomUUID().toString();
        return companies.save(Company.create("CORS " + suffix, null,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.com", null, null, null));
    }

    private String userToken(Company target, boolean exporter) {
        String suffix = UUID.randomUUID().toString();
        User user = User.create(suffix + "@example.com", UserType.COMPANY);
        user.activate(passwords.hash("Canteiro#2026Seguro"), Instant.now());
        user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), target.getId()));
        if (exporter) {
            Role role = roles.save(Role.create(target.getId(), "CORS " + suffix, null));
            var permission = permissions.findByModuleAndAction(AccessModule.MEASUREMENTS, AccessAction.EXPORT).orElseThrow();
            rolePermissions.save(RolePermission.create(role.getId(), permission.getId()));
            userRoles.save(UserRole.create(user.getId(), role.getId(), target.getId()));
        }
        return tokens.issue(user);
    }
}
