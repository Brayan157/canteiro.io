package com.renovar.canteiro.io.identity.application;

public interface PasswordResetTokenHasher {

    String hash(String rawPasswordResetToken);
}
