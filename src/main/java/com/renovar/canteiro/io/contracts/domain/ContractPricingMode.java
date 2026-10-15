package com.renovar.canteiro.io.contracts.domain;

/**
 * Defines where the commercial amount of a contract originates. A fixed
 * contract retains its independently negotiated services; a measurement based
 * contract is formed by accepted measurement compositions.
 */
public enum ContractPricingMode {
    FIXED,
    MEASUREMENT_BASED
}
