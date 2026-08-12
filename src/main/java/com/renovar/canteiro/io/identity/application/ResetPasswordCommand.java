package com.renovar.canteiro.io.identity.application;

public record ResetPasswordCommand(String passwordResetToken, String newPassword) {
}
