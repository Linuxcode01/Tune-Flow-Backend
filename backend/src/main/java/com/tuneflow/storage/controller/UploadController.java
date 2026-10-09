package com.tuneflow.storage.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.storage.dto.InitiateUploadRequest;
import com.tuneflow.storage.dto.InitiateUploadResponse;
import com.tuneflow.storage.dto.PartUploadResponse;
import com.tuneflow.storage.dto.UploadResponse;
import com.tuneflow.storage.service.ChunkedUploadService;
import com.tuneflow.storage.service.MediaUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Media upload endpoints for artists and admins (docs sections 16, 34). Uploads are rate limited and
 * role-restricted. Two routes exist to the same destination:
 *
 * <pre>
 * Small files - one request:
 * 1. POST /api/v1/uploads/audio        -> { objectKey, previewUrl, durationSeconds }
 * 2. POST /api/v1/songs  (or PUT ...)  -> persist metadata + objectKey
 *
 * Large files - chunked, resumable, retryable per chunk:
 * 1. POST   /api/v1/uploads/audio/initiate                  -> session { uploadId, chunkSizeBytes, partCount }
 * 2. PUT    /api/v1/uploads/audio/{uploadId}/parts/{n}       (raw application/octet-stream, repeatable)
 * 3. GET    /api/v1/uploads/audio/{uploadId}                -> receivedParts, for resuming
 * 4. POST   /api/v1/uploads/audio/{uploadId}/complete       -> same UploadResponse as the single-shot route
 * 5. DELETE /api/v1/uploads/audio/{uploadId}                -> discard
 * </pre>
 *
 * <p>Whichever route is used, the bytes end up behind one {@code objectKey} and the metadata call is
 * identical.
 */
@Tag(name = "Uploads", description = "Audio and artwork uploads (ARTIST/ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final MediaUploadService mediaUploadService;
    private final ChunkedUploadService chunkedUploadService;

    @Operation(summary = "Upload an audio file in one request",
            description = "Validated, optionally transcoded with FFmpeg, then stored in S3/MinIO or local storage. "
                    + "Use the chunked endpoints for anything large.")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> uploadAudio(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Audio uploaded successfully",
                mediaUploadService.uploadAudio(file)));
    }

    @Operation(summary = "Open a chunked audio upload session",
            description = "Validates the declared format and size, then returns the chunk size and part count "
                    + "the client must use. Nothing is transferred yet.")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PostMapping("/audio/initiate")
    public ResponseEntity<ApiResponse<InitiateUploadResponse>> initiateAudioUpload(
            @Valid @RequestBody InitiateUploadRequest request) {
        UUID userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(ApiResponse.success("Upload session created",
                chunkedUploadService.initiate(userId, request)));
    }

    @Operation(summary = "Upload one part",
            description = "Raw bytes (`application/octet-stream`) of a single chunk. Safe to retry: re-sending a part "
                    + "overwrites the previous attempt.")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PutMapping(value = "/audio/{uploadId}/parts/{partNumber}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiResponse<PartUploadResponse>> uploadPart(
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            HttpServletRequest request) throws IOException {
        UUID userId = SecurityUtils.requireUserId();
        try (InputStream body = request.getInputStream()) {
            return ResponseEntity.ok(ApiResponse.success("Part stored",
                    chunkedUploadService.storePart(userId, uploadId, partNumber, body)));
        }
    }

    @Operation(summary = "Session status (resume)",
            description = "Reports which parts have already been stored, so an interrupted upload can continue "
                    + "instead of starting over.")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @GetMapping("/audio/{uploadId}")
    public ResponseEntity<ApiResponse<InitiateUploadResponse>> uploadStatus(@PathVariable UUID uploadId) {
        UUID userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(ApiResponse.success("Upload session",
                chunkedUploadService.status(userId, uploadId)));
    }

    @Operation(summary = "Finish a chunked upload",
            description = "Assembles the parts, probes the duration and stores the object. Returns the same payload as "
                    + "the single-request audio upload, including the `objectKey` to publish.")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PostMapping("/audio/{uploadId}/complete")
    public ResponseEntity<ApiResponse<UploadResponse>> completeAudioUpload(@PathVariable UUID uploadId) {
        UUID userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(ApiResponse.success("Audio uploaded successfully",
                chunkedUploadService.complete(userId, uploadId)));
    }

    @Operation(summary = "Discard a chunked upload session")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @DeleteMapping("/audio/{uploadId}")
    public ResponseEntity<ApiResponse<Void>> abortAudioUpload(@PathVariable UUID uploadId) {
        UUID userId = SecurityUtils.requireUserId();
        chunkedUploadService.abort(userId, uploadId);
        return ResponseEntity.ok(ApiResponse.success("Upload session discarded", null));
    }

    @Operation(summary = "Upload artwork (album/song/artist/profile image)")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> uploadImage(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully",
                mediaUploadService.uploadImage(file)));
    }
}
