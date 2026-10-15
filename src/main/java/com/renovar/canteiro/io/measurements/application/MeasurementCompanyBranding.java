package com.renovar.canteiro.io.measurements.application;

/**
 * Private tenant branding passed to document generators. The logo never leaves the
 * backend as a public URL and is only used while rendering the document.
 */
public record MeasurementCompanyBranding(String displayName, String document, byte[] logoContent) {

    public MeasurementCompanyBranding(String displayName, byte[] logoContent) {
        this(displayName, null, logoContent);
    }

    public MeasurementCompanyBranding {
        logoContent = logoContent == null ? null : logoContent.clone();
    }

    @Override
    public byte[] logoContent() {
        return logoContent == null ? null : logoContent.clone();
    }
}
