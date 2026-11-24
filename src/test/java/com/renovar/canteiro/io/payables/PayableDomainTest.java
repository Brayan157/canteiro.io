package com.renovar.canteiro.io.payables;

import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayableDomainTest {
    @Test void requiresExplicitScopeAndDoesNotRoundOperationalMoney(){
        UUID company=UUID.randomUUID(),work=UUID.randomUUID(),group=UUID.randomUUID();
        var companyTitle=Payable.createOperational(company,PayableFinancialScope.COMPANY,null,null,group,null,null,CurrencyCode.USD,
                " Administrativo ",null,null,LocalDate.of(2026,10,22),new BigDecimal("25.50"));
        assertEquals("Administrativo",companyTitle.getReference());assertNull(companyTitle.getWorkId());
        assertEquals(CurrencyCode.USD,companyTitle.getCurrency());assertEquals(PayableStatus.OPEN,companyTitle.getStatus());
        assertEquals(CurrencyCode.USD,companyTitle.cancel().getCurrency());assertEquals(group,companyTitle.cancel().getFinancialGroupId());
        var epi=Payable.createOperational(company,PayableFinancialScope.WORK,work,null,group,null,null,CurrencyCode.BRL,"EPI",null,null,LocalDate.now(),new BigDecimal("10.00"));
        assertEquals(work,epi.getWorkId());assertNull(epi.getContractId());
        assertThrows(IllegalArgumentException.class,()->Payable.validateScope(PayableFinancialScope.COMPANY,work,null));
        assertThrows(IllegalArgumentException.class,()->Payable.validateScope(PayableFinancialScope.COMPANY,null,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->Payable.validateScope(PayableFinancialScope.WORK,null,null));
        assertThrows(IllegalArgumentException.class,()->Payable.validateScope(null,null,null));
        for(String amount:new String[]{"0.00","-1.00","1.001","100000000000000000.00"})assertThrows(IllegalArgumentException.class,()->Payable.createOperational(company,PayableFinancialScope.COMPANY,null,null,group,null,null,CurrencyCode.BRL,"Teste",null,null,LocalDate.now(),new BigDecimal(amount)));
    }
}
