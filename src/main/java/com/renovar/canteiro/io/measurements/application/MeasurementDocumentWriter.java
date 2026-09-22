package com.renovar.canteiro.io.measurements.application;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface MeasurementDocumentWriter {

    void writeTo(OutputStream outputStream) throws IOException;
}
