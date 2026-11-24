package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.application.InvoiceXmlFiscalSnapshot;
import com.renovar.canteiro.io.receivables.application.InvoiceXmlFiscalSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcInvoiceXmlFiscalSnapshotRepository implements InvoiceXmlFiscalSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Override
    public Optional<InvoiceXmlFiscalSnapshot> find(UUID companyId, UUID invoiceId) {
        return jdbc.query("""
                SELECT invoice_id, company_id, contract_id, attachment_id, source_profile, currency,
                       facts, confirmed_by, confirmed_at
                FROM invoice_xml_fiscal_snapshot
                WHERE company_id = ? AND invoice_id = ?
                """, (rs, row) -> new InvoiceXmlFiscalSnapshot(
                rs.getObject("invoice_id", UUID.class), rs.getObject("company_id", UUID.class),
                rs.getObject("contract_id", UUID.class), rs.getObject("attachment_id", UUID.class),
                rs.getString("source_profile"), rs.getString("currency"),
                json.readValue(rs.getString("facts"), InvoiceXmlFiscalSnapshot.Facts.class),
                rs.getObject("confirmed_by", UUID.class), rs.getObject("confirmed_at", OffsetDateTime.class).toInstant()),
                companyId, invoiceId).stream().findFirst();
    }

    @Override
    public boolean insert(InvoiceXmlFiscalSnapshot value, String accessKeyHash) {
        return jdbc.update("""
                INSERT INTO invoice_xml_fiscal_snapshot
                    (invoice_id, company_id, contract_id, attachment_id, source_profile, currency,
                     facts, confirmed_by, confirmed_at, access_key_sha256)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, value.invoiceId(), value.companyId(), value.contractId(), value.attachmentId(),
                value.sourceProfile(), value.currency(), json.writeValueAsString(value.facts()),
                value.confirmedBy(), value.confirmedAt().atOffset(ZoneOffset.UTC), accessKeyHash) == 1;
    }
}
