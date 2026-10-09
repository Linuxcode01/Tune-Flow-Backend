package com.tuneflow.recommendation.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.recommendation.dto.HomeFeedResponse;
import com.tuneflow.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/home} - the whole Home screen in one request (docs sections 3, 20).
 *
 * <p>Anonymous callers receive the discovery rails; authenticated callers additionally get
 * "Recently played", "Made for you", "Because you listened to…" and "Artists you follow".
 */
@Tag(name = "Home", description = "Home feed")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final RecommendationService recommendationService;

    @Operation(summary = "Home feed sections")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<ApiResponse<HomeFeedResponse>> home() {
        return ResponseEntity.ok(ApiResponse.success("Home feed fetched successfully",
                recommendationService.getHomeFeed()));
    }
}
