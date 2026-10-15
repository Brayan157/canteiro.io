package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;

/** Immutable header data shared by streamed measurement document writers. */
public record MeasurementExportData(
        Measurement measurement,
        MeasurementVersion version,
        MeasurementDiscount discount,
        MeasurementCompanyBranding companyBranding,
        java.util.function.Function<java.util.UUID,java.util.UUID> serviceIdentityResolver
) {

    public MeasurementExportData(Measurement measurement, MeasurementVersion version, MeasurementDiscount discount,
                                 MeasurementCompanyBranding companyBranding) {
        this(measurement,version,discount,companyBranding,null);
    }

    public MeasurementExportData(Measurement measurement, MeasurementVersion version, MeasurementDiscount discount) {
        this(measurement, version, discount, null);
    }

    public java.util.UUID serviceIdentity(java.util.UUID itemId) {
        return itemId==null||serviceIdentityResolver==null?null:serviceIdentityResolver.apply(itemId);
    }

    public MeasurementExportData withServiceIdentityResolver(java.util.function.Function<java.util.UUID,java.util.UUID> resolver) {
        return new MeasurementExportData(measurement,version,discount,companyBranding,resolver);
    }
}
