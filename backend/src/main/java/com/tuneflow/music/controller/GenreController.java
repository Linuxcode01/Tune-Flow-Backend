package com.tuneflow.music.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.music.dto.CreateGenreRequest;
import com.tuneflow.music.dto.GenreResponse;
import com.tuneflow.music.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /api/v1/genres} - genre browse for the Flutter client, write access for ADMIN. */
@Tag(name = "Genres", description = "Music genres")
@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @Operation(summary = "List all genres")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GenreResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Genres fetched successfully", genreService.listGenres()));
    }

    @Operation(summary = "Create genre")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<GenreResponse>> create(@Valid @RequestBody CreateGenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Genre created successfully", genreService.createGenre(request)));
    }

    @Operation(summary = "Update genre")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GenreResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody CreateGenreRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Genre updated successfully",
                genreService.updateGenre(id, request)));
    }

    @Operation(summary = "Delete genre")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        genreService.deleteGenre(id);
        return ResponseEntity.ok(ApiResponse.success("Genre deleted successfully", null));
    }
}
