package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.payables.domain.PayablePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayableSettlementCommand(UUID operationId, UUID payableId, UUID reversalOfId, BigDecimal amount,
                                       LocalDate effectiveOn, PayablePaymentMethod paymentMethod, String justification) { }
