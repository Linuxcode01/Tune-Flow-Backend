package com.tuneflow.music.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.music.dto.CreateSongRequest;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.dto.UpdateSongRequest;
import com.tuneflow.music.service.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code /api/v1/songs} - catalog read/write plus likes (docs section 13). */
@Tag(name = "Songs", description = "Song catalog, discovery rails and likes")
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    @Operation(summary = "Paginated song catalog",
            description = "Optional filters: artistId, albumId, genre (slug) or free-text query")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> list(
            @RequestParam(required = false) UUID artistId,
            @RequestParam(required = false) UUID albumId,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<SongResponse> page = songService.getSongs(artistId, albumId, genre, query, pageable);
        return ResponseEntity.ok(ApiResponse.success("Songs fetched successfully", page));
    }

    @Operation(summary = "Trending songs", description = "Cached; drives the home feed")
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> trending(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Trending songs fetched successfully",
                songService.getTrending(pageable)));
    }

    @Operation(summary = "New releases", description = "Cached; newest release dates first")
    @GetMapping("/new-releases")
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> newReleases(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("New releases fetched successfully",
                songService.getNewReleases(pageable)));
    }

    @Operation(summary = "Song details")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SongResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Song fetched successfully", songService.getSong(id)));
    }

    @Operation(summary = "Add song metadata", description = "ARTIST (own profile) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<SongResponse>> create(@Valid @RequestBody CreateSongRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Song created successfully", songService.createSong(request)));
    }

    @Operation(summary = "Update song", description = "ARTIST (own songs) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SongResponse>> update(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateSongRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Song updated successfully", songService.updateSong(id, request)));
    }

    @Operation(summary = "Delete song (soft delete)", description = "ARTIST (own songs) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        songService.deleteSong(id);
        return ResponseEntity.ok(ApiResponse.success("Song deleted successfully", null));
    }

    @Operation(summary = "Like a song")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Void>> like(@PathVariable UUID id) {
        songService.likeSong(id);
        return ResponseEntity.ok(ApiResponse.success("Song added to your library", null));
    }

    @Operation(summary = "Unlike a song")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Void>> unlike(@PathVariable UUID id) {
        songService.unlikeSong(id);
        return ResponseEntity.ok(ApiResponse.success("Song removed from your library", null));
    }
}
