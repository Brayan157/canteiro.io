package com.renovar.canteiro.io.employees.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeePaymentDataCipherTest {

    private static final String KEY = "independent-employee-payment-key-with-at-least-32-characters";

    @Test
    void encryptsAndDecryptsWithAnIndependentKey() {
        EmployeePaymentDataCipher cipher = new EmployeePaymentDataCipher(KEY, "jwt-signing-secret-that-is-also-long-enough");

        String encrypted = cipher.encrypt("123456789");

        assertEquals("123456789", cipher.decrypt(encrypted));
    }

    @Test
    void rejectsMissingOrShortKeys() {
        assertThrows(IllegalStateException.class, () -> new EmployeePaymentDataCipher("", KEY));
        assertThrows(IllegalStateException.class, () -> new EmployeePaymentDataCipher("short-key", KEY));
        assertThrows(IllegalStateException.class, () -> new EmployeePaymentDataCipher(KEY, KEY));
    }
}
