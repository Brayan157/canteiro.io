package com.renovar.canteiro.io.measurements.infrastructure.reporting;

import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Per-call decimal formatting: no shared mutable formatter or binary monetary conversion. */
public final class MeasurementReportPresentation {
    private MeasurementReportPresentation() { }

    public static String money(BigDecimal value, CurrencyCode currency) {
        if (value == null) return "Indisponível";
        return currency.getSymbol() + " " + decimal("#,##0.00").format(value.setScale(2, RoundingMode.UNNECESSARY));
    }

    public static String money(String value, CurrencyCode currency) {
        return value == null ? "Indisponível" : money(new BigDecimal(value), currency);
    }

    public static String quantity(BigDecimal value) {
        return value == null ? "-" : decimal("#,##0.####").format(value);
    }

    public static String quantity(String value) {
        return value == null ? "-" : quantity(new BigDecimal(value));
    }

    public static String measure(MeasurementItem item) {
        return switch (item.getChargeType()) {
            case SQUARE_METER, KILOGRAM_PER_SQUARE_METER -> quantity(item.getAreaSquareMeters()) + " m²";
            case LINEAR_METER, KILOGRAM_PER_LINEAR_METER -> quantity(item.getLinearMeters()) + " m";
            case UNIT -> "-";
        };
    }

    public static String factor(MeasurementItem item) {
        return switch (item.getChargeType()) {
            case KILOGRAM_PER_SQUARE_METER -> quantity(item.getKilogramsPerSquareMeter()) + " kg/m²";
            case KILOGRAM_PER_LINEAR_METER -> quantity(item.getKilogramsPerLinearMeter()) + " kg/m";
            default -> "-";
        };
    }

    public static String status(String value) {
        return switch (value) {
            case "DRAFT" -> "Em revisão";
            case "SENT" -> "Enviado";
            case "PENDING_ACCEPTANCE" -> "Aguardando aceite";
            case "ACCEPTED" -> "Aceito · protegido";
            case "REJECTED" -> "Correções solicitadas";
            case "FINALIZED" -> "Finalizado";
            case "CANCELLED" -> "Cancelado";
            default -> "Em revisão";
        };
    }

    public static String date(LocalDate date) {
        return date == null ? "-" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static DecimalFormat decimal(String pattern) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
    }
}
