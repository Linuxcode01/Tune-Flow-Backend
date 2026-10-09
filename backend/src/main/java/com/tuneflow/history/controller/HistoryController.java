package com.tuneflow.history.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.history.dto.HistoryEntryResponse;
import com.tuneflow.history.dto.HistoryStatsResponse;
import com.tuneflow.history.dto.RecordPlaybackRequest;
import com.tuneflow.history.service.HistoryService;
import com.tuneflow.music.dto.SongResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code /api/v1/history} - playback events and history (docs section 13). */
@Tag(name = "History", description = "Playback history and recently played")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @Operation(summary = "Record a playback event",
            description = "Called by the player when a track starts/progresses/completes")
    @PostMapping
    public ResponseEntity<ApiResponse<HistoryEntryResponse>> record(
            @Valid @RequestBody RecordPlaybackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Playback recorded", historyService.recordPlayback(request)));
    }

    @Operation(summary = "Paginated playback history")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<HistoryEntryResponse>>> history(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("History fetched successfully",
                historyService.getHistory(pageable)));
    }

    @Operation(summary = "Recently played songs", description = "Distinct songs, most recent first")
    @GetMapping("/recently-played")
    public ResponseEntity<ApiResponse<List<SongResponse>>> recentlyPlayed(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Recently played fetched successfully",
                historyService.getRecentlyPlayed(limit)));
    }

    @Operation(summary = "Listening statistics",
            description = "Totals, top artists/genres and a trailing seven-day play chart")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<HistoryStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success("Listening statistics fetched successfully",
                historyService.getStats()));
    }

    @Operation(summary = "Clear playback history")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clear() {
        historyService.clearHistory();
        return ResponseEntity.ok(ApiResponse.success("History cleared", null));
    }
}
