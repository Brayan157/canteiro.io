package com.renovar.canteiro.io.payables.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayableSettlement(UUID id, UUID companyId, UUID payableId, UUID operationId, UUID reversalOfId,
                                BigDecimal amount, LocalDate effectiveOn, PayablePaymentMethod paymentMethod,
                                UUID recordedBy, Instant recordedAt, String justification) { }
