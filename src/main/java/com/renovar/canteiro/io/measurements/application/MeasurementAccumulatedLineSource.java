package com.renovar.canteiro.io.measurements.application;
import java.io.IOException;
@FunctionalInterface public interface MeasurementAccumulatedLineSource {
 void consume(LineConsumer consumer) throws IOException;
 @FunctionalInterface interface LineConsumer { void accept(MeasurementAccumulatedLine line) throws IOException; }
}

