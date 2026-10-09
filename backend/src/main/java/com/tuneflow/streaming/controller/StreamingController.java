package com.tuneflow.streaming.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.streaming.dto.StreamUrlResponse;
import com.tuneflow.streaming.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code GET /api/v1/songs/{id}/stream} (docs sections 13, 17, 40).
 *
 * <p>Rate limited per user because minting signed URLs is an abuse-sensitive operation, and it
 * requires authentication: anonymous browsing can read metadata but not obtain playback access.
 */
@Tag(name = "Streaming", description = "Playback authorization via short-lived signed URLs")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;

    @Operation(summary = "Authorize playback for a song",
            description = "Returns a short-lived signed URL served by the CDN/object store, never by the API")
    @GetMapping("/songs/{id}/stream")
    public ResponseEntity<ApiResponse<StreamUrlResponse>> stream(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Stream URL generated", streamingService.createStreamUrl(id)));
    }
}
