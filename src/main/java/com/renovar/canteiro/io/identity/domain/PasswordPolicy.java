package com.renovar.canteiro.io.identity.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private static final int MINIMUM_LENGTH = 12;
    private static final int MAXIMUM_BCRYPT_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_LENGTH) {
            throw new PasswordPolicyViolationException("Password must contain at least 12 characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_BYTES) {
            throw new PasswordPolicyViolationException("Password must contain at most 72 UTF-8 bytes");
        }
        if (rawPassword.chars().noneMatch(Character::isUpperCase)) {
            throw new PasswordPolicyViolationException("Password must contain an uppercase letter");
        }
        if (rawPassword.chars().noneMatch(Character::isLowerCase)) {
            throw new PasswordPolicyViolationException("Password must contain a lowercase letter");
        }
        if (rawPassword.chars().noneMatch(Character::isDigit)) {
            throw new PasswordPolicyViolationException("Password must contain a digit");
        }
        if (rawPassword.chars().allMatch(character -> Character.isLetterOrDigit(character))) {
            throw new PasswordPolicyViolationException("Password must contain a symbol");
        }
    }
}
