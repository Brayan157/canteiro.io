package com.renovar.canteiro.io.identity.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsAValidPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Canteiro#2026Seguro"));
    }

    @Test
    void rejectsPasswordWithoutAllRequiredCharacterGroups() {
        assertThrows(PasswordPolicyViolationException.class, () -> PasswordPolicy.validate("canteiro#2026"));
        assertThrows(PasswordPolicyViolationException.class, () -> PasswordPolicy.validate("CANTEIRO#2026"));
        assertThrows(PasswordPolicyViolationException.class, () -> PasswordPolicy.validate("CanteiroSeguro"));
        assertThrows(PasswordPolicyViolationException.class, () -> PasswordPolicy.validate("Canteiro2026Seguro"));
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimit() {
        String passwordLongerThanBcryptLimit = "A1!" + "x".repeat(70);

        assertThrows(
                PasswordPolicyViolationException.class,
                () -> PasswordPolicy.validate(passwordLongerThanBcryptLimit)
        );
    }
}
