package com.renovar.canteiro.io.measurements.infrastructure.branding;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/** Converts private company-logo content into an image format supported by document renderers. */
public final class MeasurementCompanyBrandingImage {

    private static final int MAX_LOGO_DIMENSION = 2_000;

    private MeasurementCompanyBrandingImage() {
    }

    public static Optional<NormalizedLogo> normalize(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        return isPng(content) || isJpeg(content) || isWebp(content) ? normalizeToPng(content) : Optional.empty();
    }

    private static Optional<NormalizedLogo> normalizeToPng(byte[] content) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                        || reader.getWidth(0) > MAX_LOGO_DIMENSION || reader.getHeight(0) > MAX_LOGO_DIMENSION) {
                    return Optional.empty();
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    return Optional.empty();
                }
                try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(image, "png", png)) {
                        return Optional.empty();
                    }
                    return Optional.of(new NormalizedLogo(png.toByteArray(), Format.PNG));
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ignored) {
            // WebP codecs are optional in the JVM; callers use the company-name fallback.
            return Optional.empty();
        }
    }

    private static boolean isPng(byte[] content) {
        return content.length >= 8
                && content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47
                && content[4] == 0x0D && content[5] == 0x0A && content[6] == 0x1A && content[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] content) {
        return content.length >= 3
                && content[0] == (byte) 0xFF && content[1] == (byte) 0xD8 && content[2] == (byte) 0xFF;
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    public record NormalizedLogo(byte[] content, Format format) {
        public NormalizedLogo {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    public enum Format {
        PNG,
        JPEG
    }
}
