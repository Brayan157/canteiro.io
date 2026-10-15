package com.renovar.canteiro.io.receivables.api.response;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
public record ReceivableCreditResponse(UUID id,UUID receivableId,UUID reversalOfCreditId,
        @Schema(type="string",format="decimal",example="100.00") String amount,UUID changeRequestId,ChangeAuthorizationMode authorizationMode,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency){
    public ReceivableCreditResponse(UUID id,UUID receivableId,UUID reversalOfCreditId,String amount,UUID changeRequestId,ChangeAuthorizationMode authorizationMode){
        this(id,receivableId,reversalOfCreditId,amount,changeRequestId,authorizationMode,null);
    }
    public ReceivableCreditResponse withCurrency(com.renovar.canteiro.io.shared.domain.CurrencyCode value){
        return new ReceivableCreditResponse(id,receivableId,reversalOfCreditId,amount,changeRequestId,authorizationMode,value);
    }
}

