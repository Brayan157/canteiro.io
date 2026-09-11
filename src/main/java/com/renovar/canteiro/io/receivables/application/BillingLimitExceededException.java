package com.renovar.canteiro.io.receivables.application;

public class BillingLimitExceededException extends IllegalStateException {

    public BillingLimitExceededException() {
        super("Active billing allocations cannot exceed the contract net amount");
    }

    public BillingLimitExceededException(String message) {
        super(message);
    }
}
