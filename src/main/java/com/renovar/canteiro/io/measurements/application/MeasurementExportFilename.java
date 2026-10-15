package com.renovar.canteiro.io.measurements.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

final class MeasurementExportFilename {
    private static final String FALLBACK_BASENAME = "medicao";
    private static final int MAX_BASENAME_LENGTH = 120;
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private MeasurementExportFilename() {
    }

    static String pdf(String title) {
        return basename(title) + ".pdf";
    }

    static String spreadsheet(String title) {
        return basename(title) + ".xlsx";
    }

    private static String basename(String title) {
        if (title == null || title.isBlank()) {
            return FALLBACK_BASENAME;
        }
        String titleWithoutKnownExtension = title.trim()
                .replaceFirst("(?i)\\.(pdf|xlsx|xls|csv|doc|docx)$", "");
        String ascii = Normalizer.normalize(titleWithoutKnownExtension, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = ascii.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank() || WINDOWS_RESERVED_NAMES.contains(slug)) {
            return FALLBACK_BASENAME;
        }
        if (slug.length() > MAX_BASENAME_LENGTH) {
            slug = slug.substring(0, MAX_BASENAME_LENGTH).replaceFirst("-+$", "");
        }
        return slug.isBlank() ? FALLBACK_BASENAME : slug;
    }
}
