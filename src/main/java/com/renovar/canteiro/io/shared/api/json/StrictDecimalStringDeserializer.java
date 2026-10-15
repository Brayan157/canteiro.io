package com.renovar.canteiro.io.shared.api.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Rejects numeric JSON tokens so financial inputs cannot be coerced from floating point values. */
public final class StrictDecimalStringDeserializer extends StdDeserializer<String> {

    public StrictDecimalStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(this, "Decimal values must be sent as JSON strings");
        }
        return parser.getString();
    }
}
