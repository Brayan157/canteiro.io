package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Explicit operator-reviewed maintenance selection, never a company API request or inferred allocation. */
public record HistoricalReceiptLinkPlan(UUID operationId, UUID companyId, UUID contractId, UUID serviceId,
        CurrencyCode currency, List<Entry> entries) {
    public HistoricalReceiptLinkPlan {
        if (operationId == null || companyId == null || contractId == null || serviceId == null || currency == null
                || entries == null || entries.size() != 2 || entries.stream().anyMatch(e -> e == null)) reject("INVALID_PLAN");
        entries = List.copyOf(entries);
        var receipts = new HashSet<UUID>();
        var titles = new HashSet<UUID>();
        var installments = new HashSet<UUID>();
        var allocations = new HashSet<UUID>();
        var invoices = new HashSet<UUID>();
        var items = new HashSet<UUID>();
        for (var entry : entries) if (!receipts.add(entry.settlementId()) || !titles.add(entry.receivableId())
                || !installments.add(entry.installmentId()) || !allocations.add(entry.allocationId())
                || !invoices.add(entry.invoiceId()) || !items.add(entry.invoiceItemId())) reject("DUPLICATE_TARGET");
    }

    public String fingerprint() {
        StringBuilder data = new StringBuilder(operationId + "/" + companyId + "/" + contractId + "/" + serviceId + "/" + currency);
        entries.stream().sorted(java.util.Comparator.comparing(Entry::settlementId)).forEach(e -> data.append('|')
                .append(e.settlementId()).append('/').append(e.receivableId()).append('/').append(e.installmentId()).append('/')
                .append(e.allocationId()).append('/').append(e.invoiceId()).append('/').append(e.invoiceItemId()).append('/')
                .append(e.expectedAmount()).append('/').append(e.receivedOn()).append('/').append(e.invoiceNumber()).append('/').append(e.invoiceIssuedOn()));
        return sha256(data.toString());
    }

    public record Entry(UUID settlementId, UUID receivableId, UUID installmentId, UUID allocationId,
            UUID invoiceId, UUID invoiceItemId, String expectedAmount, String receivedOn, String invoiceNumber, String invoiceIssuedOn) {
        public Entry {
            if (settlementId == null || receivableId == null || installmentId == null || allocationId == null
                    || invoiceId == null || invoiceItemId == null || expectedAmount == null
                    || !expectedAmount.matches("^(0|[1-9][0-9]{0,16})\\.[0-9]{2}$")
                    || new BigDecimal(expectedAmount).signum() <= 0 || invoiceNumber == null
                    || !invoiceNumber.matches("[A-Za-z0-9._/-]{1,100}")) reject("INVALID_PLAN_ENTRY");
            try { LocalDate.parse(receivedOn); LocalDate.parse(invoiceIssuedOn); }
            catch (RuntimeException invalid) { reject("INVALID_PLAN_DATE"); }
        }
        public BigDecimal amount() { return new BigDecimal(expectedAmount); }
    }

    public record Approval(String planSha256, String stateSha256, String humanAuthorizationReference) {
        public Approval {
            if (planSha256 == null || !planSha256.matches("[a-f0-9]{64}") || stateSha256 == null || !stateSha256.matches("[a-f0-9]{64}")
                    || humanAuthorizationReference == null || !humanAuthorizationReference.matches("[A-Za-z0-9:/._ -]{1,200}")
                    || humanAuthorizationReference.isBlank()) reject("APPROVAL_REQUIRED");
        }
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA_256_UNAVAILABLE"); }
    }
    public static void reject(String reason) { throw new IllegalStateException(reason); }
}
