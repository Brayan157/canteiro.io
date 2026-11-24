package com.renovar.canteiro.io.payables;

import com.renovar.canteiro.io.payables.domain.Supplier;
import com.renovar.canteiro.io.payables.domain.FinancialCategory;
import com.renovar.canteiro.io.payables.domain.FinancialCategoryType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayableCatalogDomainTest {
    @Test void normalizesSupplierIdentityAndPreservesLogicalHistory(){
        var s=Supplier.create(UUID.randomUUID(),"  Aço e tubos  ","ab-12.34/5");
        assertEquals("Aço e tubos",s.name());assertEquals("AB12345",s.document());
        assertFalse(s.deactivate().active());assertThrows(IllegalStateException.class,()->s.deactivate().update("Outro",null));
        assertThrows(IllegalArgumentException.class,()->Supplier.create(s.companyId()," ",null));
        assertThrows(IllegalArgumentException.class,()->Supplier.create(s.companyId(),"Outro","!!!"));
    }
    @Test void categoryLabelsCanChangeButCodeAndClassificationRemainStable(){
        var c=FinancialCategory.create(UUID.randomUUID(),"epi","EPIs",null,FinancialCategoryType.WORK_COST);
        var updated=c.update("Equipamentos de proteção","Custo administrativo de classificação");
        assertEquals("EPI",updated.code());assertEquals(FinancialCategoryType.WORK_COST,updated.type());
        assertEquals(c.id(),updated.id());assertFalse(updated.deactivate().active());
        assertThrows(IllegalStateException.class,()->updated.deactivate().update("Outro",null));
        assertThrows(IllegalArgumentException.class,()->FinancialCategory.create(c.companyId(),"inválido!","Categoria",null,FinancialCategoryType.TAX));
    }
}
