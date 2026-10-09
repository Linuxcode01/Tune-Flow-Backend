package com.tuneflow.streaming.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.streaming.dto.CreateDownloadRequest;
import com.tuneflow.streaming.dto.DownloadResponse;
import com.tuneflow.streaming.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code /api/v1/downloads} - offline download authorization (docs section 26). */
@Tag(name = "Downloads", description = "Offline download authorization and licence management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final StreamingService streamingService;

    @Operation(summary = "Authorize a download", description = "Returns a short-lived URL for the device")
    @PostMapping
    public ResponseEntity<ApiResponse<DownloadResponse>> start(@Valid @RequestBody CreateDownloadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Download authorized", streamingService.startDownload(request)));
    }

    @Operation(summary = "Mark a download complete")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<DownloadResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Download completed", streamingService.completeDownload(id)));
    }

    @Operation(summary = "List downloads for the current user")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DownloadResponse>>> list(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Downloads fetched successfully",
                streamingService.getDownloads(pageable)));
    }

    @Operation(summary = "Revoke a download", description = "The client must delete its local copy")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID id) {
        streamingService.revokeDownload(id);
        return ResponseEntity.ok(ApiResponse.success("Download revoked", null));
    }
}
