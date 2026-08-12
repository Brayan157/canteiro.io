package com.renovar.canteiro.io.identity.application;

public record ActivateAccountCommand(String activationToken, String password) {
}
