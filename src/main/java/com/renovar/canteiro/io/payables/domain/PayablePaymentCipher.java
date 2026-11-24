package com.renovar.canteiro.io.payables.domain;

public interface PayablePaymentCipher {
    String encrypt(String value,String context);
    String decrypt(String value,String context);
    byte[] encryptFile(byte[] value,String context);
    java.io.InputStream decryptFile(java.io.InputStream value,String context);
}
