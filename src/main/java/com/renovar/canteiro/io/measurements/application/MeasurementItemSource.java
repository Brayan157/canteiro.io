package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementItem;

import java.io.IOException;

/** Supplies export rows in bounded batches rather than as an in-memory list. */
@FunctionalInterface
public interface MeasurementItemSource {

    void consume(MeasurementItemConsumer consumer) throws IOException;

    @FunctionalInterface
    interface MeasurementItemConsumer {
        void accept(MeasurementItem item) throws IOException;
    }
}
