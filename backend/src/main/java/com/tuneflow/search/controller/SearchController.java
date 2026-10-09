package com.tuneflow.search.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.search.dto.SearchResponse;
import com.tuneflow.search.dto.SearchSuggestionResponse;
import com.tuneflow.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/search} - cross-entity search and autocomplete (docs sections 13, 24). */
@Tag(name = "Search", description = "Search songs, artists, albums and playlists")
@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Search the catalog",
            description = "Songs, artists, albums and playlists in one response; cached per query")
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam("q") @NotBlank String query,
            @Parameter(description = "all | songs | artists | albums | playlists")
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Search completed",
                searchService.search(query, type, limit)));
    }

    @Operation(summary = "Autocomplete suggestions", description = "Backs the search box as the user types")
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<SearchSuggestionResponse>> suggest(
            @RequestParam("q") @NotBlank String query,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Suggestions fetched successfully",
                searchService.suggest(query, limit)));
    }
}
