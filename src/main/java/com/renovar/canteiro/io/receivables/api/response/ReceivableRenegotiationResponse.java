package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReceivableRenegotiationResponse(UUID sourceReceivableId,List<Title> titles,UUID changeRequestId,ChangeAuthorizationMode mode,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency) {
    public ReceivableRenegotiationResponse(UUID sourceReceivableId,List<Title> titles,UUID changeRequestId,ChangeAuthorizationMode mode){
        this(sourceReceivableId,titles,changeRequestId,mode,null);
    }
    public ReceivableRenegotiationResponse withCurrency(com.renovar.canteiro.io.shared.domain.CurrencyCode value){
        return new ReceivableRenegotiationResponse(sourceReceivableId,titles,changeRequestId,mode,value);
    }
    public record Title(UUID id,UUID billingInstallmentId,UUID financialGroupId,LocalDate dueOn,
        @Schema(type="string",format="decimal",example="500.00") String originalAmount,ReceivableStatus status) { }
}
