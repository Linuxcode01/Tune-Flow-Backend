package com.tuneflow.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Autocomplete payload for the search box (docs section 24: prefix search and autocomplete). */
@Schema(name = "SearchSuggestionResponse")
public record SearchSuggestionResponse(
        String query,
        List<String> terms,
        List<Suggestion> items
) {

    @Schema(name = "Suggestion", description = "Minimal item the client can render in the dropdown")
    public record Suggestion(String type, String id, String title, String subtitle, String imageUrl) {
    }
}
