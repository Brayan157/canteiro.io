package com.renovar.canteiro.io.receivables.infrastructure.maintenance;

import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan;
import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.Approval;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Separate operator command. Does not start SpringApplication, a server, jobs, migrations or seeds. */
public final class HistoricalReceiptLinkMaintenanceCommand {
    private HistoricalReceiptLinkMaintenanceCommand() { }

    public static void main(String[] args) {
        var logger = LoggerFactory.getLogger(HistoricalReceiptLinkMaintenanceCommand.class);
        try {
            Options options = Options.parse(args);
            if (Files.size(options.plan()) > 32768) HistoricalReceiptLinkPlan.reject("INVALID_MANIFEST");
            var json = new ObjectMapper();
            var tree = json.readTree(Files.readString(options.plan()));
            if (!tree.path("entries").isArray()) HistoricalReceiptLinkPlan.reject("INVALID_MANIFEST");
            for (var entry : tree.path("entries")) if (!entry.path("expectedAmount").isString()) HistoricalReceiptLinkPlan.reject("INVALID_MANIFEST");
            var plan = json.treeToValue(tree, HistoricalReceiptLinkPlan.class);
            String url = environment("DATABASE_URL", "SPRING_DATASOURCE_URL");
            if (!url.startsWith("jdbc:postgresql://") || url.contains("@") || url.matches("(?i).*(password|user|sslpassword)=.*")) HistoricalReceiptLinkPlan.reject("UNSAFE_DATABASE_URL");
            var source = new DriverManagerDataSource(url, environment("DATABASE_USERNAME", "SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"),
                    environment("DATABASE_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD"));
            var maintenance = new HistoricalReceiptLinkMaintenance(new JdbcTemplate(source), new DataSourceTransactionManager(source), Clock.systemUTC());
            var result = options.apply() ? maintenance.apply(plan, options.approval()) : maintenance.validate(plan);
            logger.info("Maintenance result status={} planSha256={} stateSha256={} alreadyApplied={} linkCount={}",
                    result.status(), result.planSha256(), result.stateSha256(), result.alreadyApplied(), result.linkCount());
        } catch (IllegalStateException refused) {
            // Exception payloads, JDBC credentials, manifest values and financial IDs are never printed.
            String reason = refused.getMessage();
            logger.error("Maintenance refused: {}", reason != null && reason.matches("[A-Z_]{1,80}") ? reason : "INVALID_MANIFEST");
            System.exit(2);
        } catch (Exception failed) {
            logger.error("Maintenance refused: MAINTENANCE_FAILED");
            System.exit(2);
        }
    }

    private static String environment(String... keys) {
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) return value;
        }
        HistoricalReceiptLinkPlan.reject("DATABASE_ENVIRONMENT_REQUIRED");
        throw new IllegalStateException("DATABASE_ENVIRONMENT_REQUIRED");
    }

    public record Options(Path plan, boolean apply, Approval approval) {
        public static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            boolean apply = false;
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (key.equals("--apply")) {
                    if (apply) HistoricalReceiptLinkPlan.reject("INVALID_ARGUMENTS");
                    apply = true;
                } else {
                    if (!java.util.Set.of("--plan", "--approved-plan-sha256", "--expected-state-sha256", "--approval-reference").contains(key)
                            || ++i >= args.length || args[i].startsWith("--") || values.put(key, args[i]) != null) HistoricalReceiptLinkPlan.reject("INVALID_ARGUMENTS");
                }
            }
            if (!values.containsKey("--plan")) HistoricalReceiptLinkPlan.reject("PLAN_FILE_REQUIRED");
            Approval approval = apply ? new Approval(values.get("--approved-plan-sha256"), values.get("--expected-state-sha256"), values.get("--approval-reference")) : null;
            if (!apply && values.size() != 1) HistoricalReceiptLinkPlan.reject("INVALID_ARGUMENTS");
            return new Options(Path.of(values.get("--plan")), apply, approval);
        }
    }
}
