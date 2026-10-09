package com.tuneflow.recommendation.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /api/v1/recommendations} - personalised feeds (docs sections 13, 25). */
@Tag(name = "Recommendations", description = "Personalised recommendations (rule-based -> collaborative -> hybrid)")
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "Personalised recommendations",
            description = "Personalised when authenticated; trending for anonymous callers")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SongResponse>>> personalized(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Recommendations fetched successfully",
                recommendationService.getPersonalized(limit)));
    }

    @Operation(summary = "Similar songs", description = "\"More like this\" for a given track")
    @GetMapping("/similar/{songId}")
    public ResponseEntity<ApiResponse<List<SongResponse>>> similar(@PathVariable UUID songId,
                                                                  @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Similar songs fetched successfully",
                recommendationService.getSimilar(songId, limit)));
    }

    @Operation(summary = "Active recommendation strategy", description = "Diagnostics/observability")
    @GetMapping("/strategy")
    public ResponseEntity<ApiResponse<Map<String, String>>> strategy() {
        return ResponseEntity.ok(ApiResponse.success("Active strategy fetched successfully",
                Map.of("strategy", recommendationService.activeStrategy())));
    }
}
