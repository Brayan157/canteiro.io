package com.renovar.canteiro.io.shared.api.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdScalarSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Serializes official money without binary conversion, scientific notation or implicit rounding. */
public final class CanonicalMoneySerializer extends StdScalarSerializer<BigDecimal> {
    public CanonicalMoneySerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        generator.writeString(value.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    }
}
