package com.renovar.canteiro.io.platform.support.application;

public record RecordSupportReportDeliveryCommand(
        String reportType,
        String recipientEmail,
        String artifactReference
) {

    public RecordSupportReportDeliveryCommand {
        reportType = requireText(reportType, "Report type", 100);
        recipientEmail = requireText(recipientEmail, "Recipient email", 255);
        artifactReference = requireText(artifactReference, "Artifact reference", 500);
    }

    private static String requireText(String value, String fieldName, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must have between 1 and " + maximumLength + " characters");
        }
        return value.trim();
    }
}
