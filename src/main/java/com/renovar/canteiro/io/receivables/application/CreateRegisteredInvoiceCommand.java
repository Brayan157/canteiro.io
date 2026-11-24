package com.renovar.canteiro.io.receivables.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateRegisteredInvoiceCommand(
        UUID contractId,
        String number,
        String series,
        String accessKey,
        LocalDate issuedOn,
        List<InvoiceItemCommand> items,
        String justification,
        java.math.BigDecimal documentTotalAmount
) {
    public CreateRegisteredInvoiceCommand(UUID contractId,String number,String series,String accessKey,LocalDate issuedOn,List<InvoiceItemCommand> items,String justification){
        this(contractId,number,series,accessKey,issuedOn,items,justification,null);
    }
}
