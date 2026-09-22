package com.renovar.canteiro.io.employees.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Map;

@Component
public class PixStaticQrCodeGenerator {

    private static final String PIX_GUI = "br.gov.bcb.pix";

    public PixStaticQrCode generate(String pixKey, String receiverName, String receiverCity) {
        String payload = payload(pixKey, receiverName, receiverCity);
        return new PixStaticQrCode(payload, "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCode(payload)));
    }

    public void validate(String pixKey, String receiverName, String receiverCity) {
        payload(pixKey, receiverName, receiverCity);
    }

    private String payload(String pixKey, String receiverName, String receiverCity) {
        String key = required(pixKey, "Pix key");
        String name = brCodeText(receiverName, 25, "Pix receiver name");
        String city = brCodeText(receiverCity, 15, "Pix receiver city");
        String merchantAccount = field("00", PIX_GUI) + field("01", key);
        String withoutCrc = field("00", "01") + field("26", merchantAccount) + field("52", "0000")
                + field("53", "986") + field("58", "BR") + field("59", name) + field("60", city)
                + field("62", field("05", "***")) + "6304";
        return withoutCrc + crc16(withoutCrc);
    }

    private byte[] qrCode(String payload) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256,
                    Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Pix QR Code cannot be generated", exception);
        }
    }

    private String field(String id, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > 99) throw new IllegalArgumentException("Pix BR Code field is too long");
        return id + String.format(java.util.Locale.ROOT, "%02d", length) + value;
    }

    private String crc16(String value) {
        int crc = 0xFFFF;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (current & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(java.util.Locale.ROOT, "%04X", crc);
    }

    private String brCodeText(String value, int maxLength, String fieldName) {
        String normalized = Normalizer.normalize(required(value, fieldName), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must contain between 1 and " + maxLength + " BR Code characters");
        }
        return normalized;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        return value.trim();
    }

    public record PixStaticQrCode(String copyPaste, String qrCodeDataUrl) { }
}
