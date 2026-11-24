package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.WorkBillingReportRepository.ContractPosition;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

public interface WorkBillingPdfGenerator {
    ExportResult writeWork(Header header, Contracts contracts, OutputStream output) throws IOException;

    record Header(String workName, String workReference) { }

    record ExportResult(long contractRows, int pages) { }

    @FunctionalInterface
    interface Contracts {
        List<ReadWorkBillingControlUseCase.CurrencyTotal> scan(Consumer<ContractPosition> consumer);
    }
}
