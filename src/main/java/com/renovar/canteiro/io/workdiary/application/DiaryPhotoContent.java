package com.renovar.canteiro.io.workdiary.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

record DiaryPhotoContent(byte[] bytes, String contentType, String sha256) {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SIDE = 6000;
    private static final int MAX_PIXELS = 16_000_000;

    static DiaryPhotoContent read(InputStream stream) {
        try {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new ApiException(HttpStatus.valueOf(413), ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Diary photo exceeds the 8 MiB limit");
            }
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw invalid();
                }
                var reader = readers.next();
                try {
                    String type = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                        case "jpeg" -> "image/jpeg";
                        case "png" -> "image/png";
                        default -> throw invalid();
                    };
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                            || (long) width * height > MAX_PIXELS) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION,
                                "Diary photo exceeds the supported image dimensions");
                    }
                    if (reader.read(0) == null) {
                        throw invalid();
                    }
                    return new DiaryPhotoContent(bytes, type, digest(bytes));
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION,
                "A valid JPEG or PNG photo is required");
    }
}
