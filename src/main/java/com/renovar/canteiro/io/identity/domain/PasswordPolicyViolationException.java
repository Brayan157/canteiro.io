package com.renovar.canteiro.io.identity.domain;

public class PasswordPolicyViolationException extends IllegalArgumentException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
