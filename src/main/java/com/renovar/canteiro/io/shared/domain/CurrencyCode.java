package com.renovar.canteiro.io.shared.domain;

/** Supported monetary units. Amounts are never converted between currencies. */
public enum CurrencyCode {
    BRL("R$", "Real brasileiro"),
    USD("US$", "Dólar americano"),
    EUR("€", "Euro");

    private final String symbol;
    private final String displayName;

    CurrencyCode(String symbol, String displayName) {
        this.symbol = symbol;
        this.displayName = displayName;
    }

    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public int getDecimalPlaces() { return 2; }

    public static CurrencyCode defaultIfAbsent(CurrencyCode currency) {
        return currency == null ? BRL : currency;
    }

    public void requireSame(CurrencyCode other) {
        if (this != other) {
            throw new IllegalStateException("Currencies must match; currency conversion is not supported");
        }
    }
}
