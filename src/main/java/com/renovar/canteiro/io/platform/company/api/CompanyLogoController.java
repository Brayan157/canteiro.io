package com.renovar.canteiro.io.platform.company.api;

import com.renovar.canteiro.io.platform.company.application.CompanyProfileApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company/logo")
public class CompanyLogoController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_LOGO_SIZE_BYTES = 2 * 1024 * 1024;

    private final CompanyProfileApplicationService companyProfileApplicationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploads the authenticated company's logo")
    public LogoUploadResponse upload(@NotNull @RequestPart("file") MultipartFile file) throws java.io.IOException {
        byte[] content = file.getBytes();
        if (file.isEmpty() || file.getSize() > MAX_LOGO_SIZE_BYTES || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())
                || !hasExpectedSignature(content, file.getContentType())) {
            throw new IllegalArgumentException("Logo must be a PNG, JPEG or WebP image up to 2 MiB");
        }
        return new LogoUploadResponse(companyProfileApplicationService.uploadLogo(content, file.getContentType()));
    }

    @GetMapping
    @Operation(summary = "Downloads the authenticated company's logo")
    public ResponseEntity<byte[]> download() {
        CompanyProfileApplicationService.CompanyLogoContent logo = companyProfileApplicationService.loadLogo();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).body(logo.content());
    }

    public record LogoUploadResponse(String logoUrl) { }

    private boolean hasExpectedSignature(byte[] content, String contentType) {
        if (content.length < 12) return false;
        return switch (contentType) {
            case "image/png" -> content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47;
            case "image/jpeg" -> content[0] == (byte) 0xff && content[1] == (byte) 0xd8 && content[2] == (byte) 0xff;
            case "image/webp" -> content[0] == 0x52 && content[1] == 0x49 && content[2] == 0x46 && content[3] == 0x46
                    && content[8] == 0x57 && content[9] == 0x45 && content[10] == 0x42 && content[11] == 0x50;
            default -> false;
        };
    }
}
