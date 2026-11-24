package com.renovar.canteiro.io.reporting.application;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

public interface CashFlowPdfGenerator {
    void write(Data data, OutputStream output) throws IOException;

    record Data(String companyName, String workName, CurrencyCode currency, LocalDate from, LocalDate to,
                LocalDate asOf, LocalDate dueThrough, ReadRealizedCashUseCase.CurrencyPosition realized,
                ReadCashDueUseCase.CurrencyPosition due, CompareCashPaymentsUseCase.Period previousPeriod,
                CompareCashPaymentsUseCase.CurrencyPosition comparison) { }
}
