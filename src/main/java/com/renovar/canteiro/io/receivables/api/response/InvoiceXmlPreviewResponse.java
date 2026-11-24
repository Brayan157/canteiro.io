package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.PreviewInvoiceXmlUseCase;
import com.renovar.canteiro.io.receivables.application.NfseXmlPreviewParser;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description="Stateless assisted XML preview; never an official invoice, fiscal validation, tax obligation, payment, attachment or reservation. All monetary source values are canonical decimal strings with provenance; absence is unavailable. Requires human review and official full-service coverage on subsequent manual registration.")
public record InvoiceXmlPreviewResponse(UUID contractId,String currency,ChangeAuthorizationMode creationMode,
        Source source,NfseXmlPreviewParser.Header prefill,NfseXmlPreviewParser.Dps dps,NfseXmlPreviewParser.Totals sourceTotals,
        List<NfseXmlPreviewParser.TaxCandidate> taxCandidates,java.util.Map<String,NfseXmlPreviewParser.Field> additionalTaxFields,PartyChecks partyChecks,InvoiceCoverageResponse billingContext,
        boolean requiresHumanReview,boolean hasBlockingIssues,List<NfseXmlPreviewParser.Issue> issues){
    @Schema(name="InvoiceXmlSource")
    public record Source(String profile,String schemaValidationStatus,boolean signaturePresent,String signatureValidationStatus,boolean substitutionIndicated) { }
    @Schema(name="InvoiceXmlPartyChecks")
    public record PartyChecks(PreviewInvoiceXmlUseCase.PartyMatch issuer,PreviewInvoiceXmlUseCase.PartyMatch customer) { }
    public static InvoiceXmlPreviewResponse from(PreviewInvoiceXmlUseCase.View value){var p=value.parsed();return new InvoiceXmlPreviewResponse(value.contractId(),value.currency(),value.mode(),
            new Source("NFSE_NACIONAL_1_01","NOT_PERFORMED",p.signaturePresent(),p.signaturePresent()?"NOT_VERIFIED":"NOT_PRESENT",p.substitutionIndicated()),
            p.header(),p.dps(),p.sourceTotals(),p.taxCandidates(),p.additionalTaxFields(),new PartyChecks(value.issuerMatch(),value.customerMatch()),
            value.billingContext()==null?null:InvoiceCoverageResponse.from(value.billingContext()),true,value.issues().stream().anyMatch(NfseXmlPreviewParser.Issue::blocking),value.issues());}
    @Override public String toString(){return "InvoiceXmlPreviewResponse[redacted]";}
}
