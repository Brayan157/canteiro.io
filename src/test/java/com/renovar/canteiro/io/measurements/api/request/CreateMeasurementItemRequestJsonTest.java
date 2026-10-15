package com.renovar.canteiro.io.measurements.api.request;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateMeasurementItemRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void acceptsUnitPriceOnlyAsJsonString() throws Exception {
        CreateMeasurementItemRequest request = mapper.readValue("""
                {"itemNumber":1,"serviceTitle":"Estrutura","activity":"Fabricar","measuredOn":"2026-06-04",
                 "chargeType":"SQUARE_METER","unitPrice":"25.00","previewTotalAmount":"250.00"}
                """, CreateMeasurementItemRequest.class);

        assertEquals("25.00", request.unitPrice());
        assertEquals("250.00", request.previewTotalAmount());
    }

    @Test
    void rejectsNumericUnitPriceInsteadOfCoercingItToText() {
        assertThrows(DatabindException.class, () -> mapper.readValue("""
                {"itemNumber":1,"serviceTitle":"Estrutura","activity":"Fabricar","measuredOn":"2026-06-04",
                 "chargeType":"SQUARE_METER","unitPrice":25.00}
                """, CreateMeasurementItemRequest.class));
    }

    @Test
    void rejectsNumericFrontendPreviewInsteadOfCoercingItToText() {
        assertThrows(DatabindException.class, () -> mapper.readValue("""
                {"itemNumber":1,"serviceTitle":"Estrutura","activity":"Fabricar","measuredOn":"2026-06-04",
                 "chargeType":"SQUARE_METER","unitPrice":"25.00","previewTotalAmount":250.00}
                """, CreateMeasurementItemRequest.class));
    }
}
