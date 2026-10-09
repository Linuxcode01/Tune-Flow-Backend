package com.tuneflow.album.controller;

import com.tuneflow.album.dto.AlbumResponse;
import com.tuneflow.album.dto.CreateAlbumRequest;
import com.tuneflow.album.dto.UpdateAlbumRequest;
import com.tuneflow.album.service.AlbumService;
import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.music.dto.SongResponse;
import io.swagger.v3.oas.annotations.Operation;
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

/** {@code /api/v1/albums} - album details, track lists and release rails (docs section 13). */
@Tag(name = "Albums", description = "Album catalog")
@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @Operation(summary = "Paginated album catalog", description = "Filter by artistId or free-text query")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AlbumResponse>>> list(
            @RequestParam(required = false) UUID artistId,
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Albums fetched successfully",
                albumService.getAlbums(artistId, query, pageable)));
    }

    @Operation(summary = "New album releases")
    @GetMapping("/new-releases")
    public ResponseEntity<ApiResponse<PageResponse<AlbumResponse>>> newReleases(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("New releases fetched successfully",
                albumService.getNewReleases(pageable)));
    }

    @Operation(summary = "Album details")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlbumResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Album fetched successfully", albumService.getAlbum(id)));
    }

    @Operation(summary = "Album tracks in playing order")
    @GetMapping("/{id}/songs")
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> songs(@PathVariable UUID id,
                                                                        @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Album songs fetched successfully",
                albumService.getAlbumSongs(id, pageable)));
    }

    @Operation(summary = "Create album", description = "ARTIST (own profile) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<AlbumResponse>> create(@Valid @RequestBody CreateAlbumRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Album created successfully", albumService.createAlbum(request)));
    }

    @Operation(summary = "Update album", description = "ARTIST (own albums) or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AlbumResponse>> update(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateAlbumRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Album updated successfully",
                albumService.updateAlbum(id, request)));
    }

    @Operation(summary = "Delete album", description = "Tracks are detached, not deleted")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        albumService.deleteAlbum(id);
        return ResponseEntity.ok(ApiResponse.success("Album deleted successfully", null));
    }
}
