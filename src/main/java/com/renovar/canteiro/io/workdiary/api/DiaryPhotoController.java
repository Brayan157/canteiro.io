package com.renovar.canteiro.io.workdiary.api;

import com.renovar.canteiro.io.workdiary.application.DiaryPhotoService;
import com.renovar.canteiro.io.workdiary.domain.DiaryPhoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/works/{workId}/diary/{date}/photos")
@RequiredArgsConstructor
public class DiaryPhotoController {
    private final DiaryPhotoService photos;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Attaches one private photo to an open diary revision",
            description = "Direct WORK_DIARY update authority and a current UPDATE grant for this work are required. "
                    + "Send file, expectedRevision, operationId and exactly one of activityId or occurrenceId in multipart/form-data. "
                    + "The item must exist in the requested open revision. Accepts JPEG/PNG up to 8 MiB, "
                    + "6000 pixels per side and 16 million pixels.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = DiaryPhotoUploadRequest.class))))
    @ApiResponse(responseCode = "201", description = "Photo attached", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "200", description = "Same upload replayed", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    @ApiResponse(responseCode = "413", ref = "#/components/responses/ProblemDetail")
    public ResponseEntity<DiaryPhotoResponse> uploadDiaryPhoto(@PathVariable UUID workId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @Valid @ModelAttribute DiaryPhotoUploadRequest request) throws IOException {
        try (var stream = request.file().getInputStream()) {
            var result = photos.upload(workId, date,
                    new DiaryPhotoService.UploadCommand(request.operationId(), request.expectedRevision(), stream,
                            new DiaryPhoto.Target(request.activityId(), request.occurrenceId())));
            return ResponseEntity.status(result.replay() ? 200 : 201).cacheControl(CacheControl.noStore())
                    .body(DiaryPhotoResponse.from(result.photo()));
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists private diary photo metadata",
            description = "Requires WORK_DIARY.READ and a current READ grant for this work. Without revision, lists "
                    + "visible photos for the day. An activityId or occurrenceId filter includes visible photos from any "
                    + "revision for the same item if it exists in the current revision. "
                    + "With revision, lists its original attachments including withdrawal metadata.")
    public ResponseEntity<List<DiaryPhotoResponse>> listDiaryPhotos(@PathVariable UUID workId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @PositiveOrZero Long revision,
            @RequestParam(required = false) UUID activityId,
            @RequestParam(required = false) UUID occurrenceId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(photos.list(workId, date, revision, new DiaryPhoto.Target(activityId, occurrenceId), offset, limit)
                        .stream().map(DiaryPhotoResponse::from).toList());
    }

    @GetMapping("/{photoId}/content")
    @Operation(summary = "Reads an existing private diary photo", description = "Requires a current READ grant and "
            + "WORK_DIARY.READ. The original bytes are returned after an integrity check. Withdrawn photos return 404.")
    @ApiResponse(responseCode = "200", description = "Private photo bytes",
            content = {@Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public ResponseEntity<byte[]> readDiaryPhotoContent(@PathVariable UUID workId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @PathVariable UUID photoId) {
        var content = photos.load(workId, date, photoId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType())).contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff").body(content.bytes());
    }

    @PutMapping(path = "/{photoId}/withdraw", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Withdraws a diary photo while preserving its history",
            description = "Requires direct update authority, a current UPDATE grant for this work, a reason, and "
                    + "the current open revision number. Withdrawal hides the bytes from API readers.")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    public ResponseEntity<DiaryPhotoResponse> withdrawDiaryPhoto(@PathVariable UUID workId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @PathVariable UUID photoId,
            @Valid @RequestBody DiaryPhotoWithdrawRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(DiaryPhotoResponse.from(photos.withdraw(workId, date, photoId, request.expectedRevision(), request.reason())));
    }

    @Schema(description = "Multipart file upload to one exact item. Supply either activityId or occurrenceId.")
    public record DiaryPhotoUploadRequest(@NotNull UUID operationId, @NotNull @PositiveOrZero Long expectedRevision,
                                          @NotNull @Schema(type = "string", format = "binary") MultipartFile file,
                                          UUID activityId, UUID occurrenceId) {
    }

    public record DiaryPhotoWithdrawRequest(@NotNull @PositiveOrZero Long expectedRevision,
                                            @NotBlank @Size(max = 500) String reason) {
    }

    public record DiaryPhotoResponse(UUID id, long revision, String contentType, int sizeBytes, UUID uploadedBy,
                                     Instant uploadedAt, UUID withdrawnBy, Instant withdrawnAt, String withdrawalReason,
                                     UUID activityId, UUID occurrenceId) {
        static DiaryPhotoResponse from(DiaryPhoto photo) {
            return new DiaryPhotoResponse(photo.id(), photo.revision(), photo.contentType(), photo.sizeBytes(), photo.uploadedBy(),
                    photo.uploadedAt(), photo.withdrawnBy(), photo.withdrawnAt(), photo.withdrawalReason(),
                    photo.target().activityId(), photo.target().occurrenceId());
        }
    }
}
