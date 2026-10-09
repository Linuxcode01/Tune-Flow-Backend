package com.tuneflow.lyrics.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.lyrics.dto.LyricsResponse;
import com.tuneflow.lyrics.dto.UpsertLyricsRequest;
import com.tuneflow.lyrics.service.LyricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /api/v1/songs/{id}/lyrics} - normal and synchronized lyrics (docs sections 13, 27). */
@Tag(name = "Lyrics", description = "Normal and synchronized lyrics")
@RestController
@RequestMapping("/api/v1/songs/{songId}/lyrics")
@RequiredArgsConstructor
public class LyricsController {

    private final LyricsService lyricsService;

    @Operation(summary = "Get lyrics",
            description = "Returns timestamped `lines` when the lyrics are synchronized")
    @GetMapping
    public ResponseEntity<ApiResponse<LyricsResponse>> get(@PathVariable UUID songId,
                                                          @RequestParam(required = false) String language) {
        return ResponseEntity.ok(ApiResponse.success("Lyrics fetched successfully",
                lyricsService.getLyrics(songId, language)));
    }

    @Operation(summary = "List available lyric languages")
    @GetMapping("/languages")
    public ResponseEntity<ApiResponse<List<String>>> languages(@PathVariable UUID songId) {
        return ResponseEntity.ok(ApiResponse.success("Languages fetched successfully",
                lyricsService.getAvailableLanguages(songId)));
    }

    @Operation(summary = "Create or update lyrics", description = "ARTIST (own songs) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PutMapping
    public ResponseEntity<ApiResponse<LyricsResponse>> upsert(@PathVariable UUID songId,
                                                             @Valid @RequestBody UpsertLyricsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lyrics saved successfully",
                lyricsService.upsertLyrics(songId, request)));
    }

    @Operation(summary = "Delete lyrics for a language", description = "ARTIST (own songs) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @DeleteMapping("/{language}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID songId, @PathVariable String language) {
        lyricsService.deleteLyrics(songId, language);
        return ResponseEntity.ok(ApiResponse.success("Lyrics deleted successfully", null));
    }
}
