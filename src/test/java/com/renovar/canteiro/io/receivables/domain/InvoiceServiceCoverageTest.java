package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InvoiceServiceCoverageTest {
    final UUID company=UUID.randomUUID(),contract=UUID.randomUUID(),installment=UUID.randomUUID(),service=UUID.randomUUID();
    @Test void completeGroupIncludesEveryBaseAndMeasurementOriginWithoutMergingAnotherCharge(){
        var a=line(installment,null,"40.00");var b=line(installment,UUID.randomUUID(),"60.00");var c=line(UUID.randomUUID(),null,"100.00");
        var groups=InvoiceServiceCoverage.groups(company,contract,List.of(a,b,c),Map.of(a.getId(),money("0"),b.getId(),money("0"),c.getId(),money("0")));
        assertEquals(2,groups.size());
        assertThrows(InvoiceServiceCoverageViolation.class,()->InvoiceServiceCoverage.requireCompleteSelection(groups,Map.of(a.getId(),money("40"))));
        InvoiceServiceCoverage.requireCompleteSelection(groups,Map.of(a.getId(),money("40"),b.getId(),money("60")));
        InvoiceServiceCoverage.requireCompleteSelection(groups,Map.of(c.getId(),money("100")));
        assertEquals(b.getMeasurementContractCompositionId(),groups.stream().flatMap(g->g.origins().stream()).filter(o->o.billingAllocationId().equals(b.getId())).findFirst().orElseThrow().measurementContractCompositionId());
    }
    @Test void legacyPartialIsVisibleAndCannotBeSilentlyCompletedByASecondInvoice(){
        var a=line(installment,null,"100.00");var groups=InvoiceServiceCoverage.groups(company,contract,List.of(a),Map.of(a.getId(),money("40")));
        assertEquals(InvoiceServiceCoverage.State.LEGACY_PARTIAL,groups.getFirst().state());assertEquals(money("40"),groups.getFirst().documentedAmount());
        assertEquals("INVOICE_SERVICE_GROUP_LEGACY_PARTIAL",assertThrows(InvoiceServiceCoverageViolation.class,()->InvoiceServiceCoverage.requireCompleteSelection(groups,Map.of(a.getId(),money("60")))).reason());
    }
    @Test void stableIdentitySurvivesOrderAndTitlesButNotTenantOrChargeAndDoesNotHideChangedMembership(){
        var a=line(installment,null,"100.00");var b=line(installment,null,"20.00");
        var first=InvoiceServiceCoverage.groups(company,contract,List.of(a),Map.of(a.getId(),money("0"))).getFirst();
        var second=InvoiceServiceCoverage.groups(company,contract,List.of(b,a),Map.of(a.getId(),money("0"),b.getId(),money("0"))).getFirst();
        assertEquals(first.id(),second.id());assertEquals(2,second.origins().size());
        var other=line(UUID.randomUUID(),null,"100.00");assertNotEquals(first.id(),InvoiceServiceCoverage.groups(company,contract,List.of(other),Map.of(other.getId(),money("0"))).getFirst().id());
        assertThrows(InvoiceServiceCoverageViolation.class,()->InvoiceServiceCoverage.requireCompleteSelection(List.of(second),Map.of(a.getId(),money("100"))));
    }
    @Test void aWholeAlreadyDocumentedGroupAndUnsupportedLegacyDiscountCannotBeSelected(){
        var a=line(installment,null,"100.00");var done=InvoiceServiceCoverage.groups(company,contract,List.of(a),Map.of(a.getId(),money("100")));
        assertEquals(InvoiceServiceCoverage.State.FULLY_DOCUMENTED,done.getFirst().state());
        assertThrows(InvoiceServiceCoverageViolation.class,()->InvoiceServiceCoverage.requireCompleteSelection(done,Map.of(a.getId(),money("100"))));
        var negative=BillingAllocation.createContractDiscount(company,installment,contract,money("-10")).activate();
        var review=InvoiceServiceCoverage.groups(company,contract,List.of(a,negative),Map.of(a.getId(),money("0")));
        assertEquals(InvoiceServiceCoverage.State.REVIEW_REQUIRED,review.getFirst().state());
    }
    BillingAllocation line(UUID charge,UUID composition,String value){return BillingAllocation.rehydrate(UUID.randomUUID(),company,charge,contract,service,composition,
            BillingAllocationType.SERVICE,money(value),BillingAllocationStatus.ACTIVE,Instant.now(),Instant.now());}
    BigDecimal money(String text){return new BigDecimal(text).setScale(2);}
}
