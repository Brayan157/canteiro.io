package com.renovar.canteiro.io.receivables.domain;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
public record ReceivableCredit(UUID id,UUID companyId,UUID receivableId,UUID contractId,BigDecimal amount,
        String reason,UUID reversalOfCreditId,UUID actorUserId,Instant createdAt){}

