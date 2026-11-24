package com.renovar.canteiro.io.receivables.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceXmlFiscalSnapshot(UUID invoiceId, UUID companyId, UUID contractId, UUID attachmentId,
                                       String sourceProfile, String currency, Facts facts,
                                       UUID confirmedBy, Instant confirmedAt) {
    @Schema(name = "InvoiceXmlFiscalFacts", description = "Confirmed source facts with XML provenance. Omitted fields are unknown. No official tax, credit, expense or receipt is implied.")
    public record Facts(NfseXmlPreviewParser.Field number, NfseXmlPreviewParser.Field processedAt,
                        NfseXmlPreviewParser.Dps dps, NfseXmlPreviewParser.Totals totals,
                        List<NfseXmlPreviewParser.TaxCandidate> taxes,
                        Map<String, NfseXmlPreviewParser.Field> reportedFields,
                        boolean signaturePresent, boolean substitutionIndicated,
                        List<String> issueCodes) { }
}
