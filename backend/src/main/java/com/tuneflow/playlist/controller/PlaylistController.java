package com.tuneflow.playlist.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.playlist.dto.AddSongRequest;
import com.tuneflow.playlist.dto.CreatePlaylistRequest;
import com.tuneflow.playlist.dto.PlaylistDetailResponse;
import com.tuneflow.playlist.dto.PlaylistResponse;
import com.tuneflow.playlist.dto.ReorderPlaylistRequest;
import com.tuneflow.playlist.dto.UpdatePlaylistRequest;
import com.tuneflow.playlist.service.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** {@code /api/v1/playlists} - playlist CRUD, membership and ordering (docs section 13). */
@Tag(name = "Playlists", description = "Create, edit and order playlists")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    @Operation(summary = "Create a playlist")
    @PostMapping
    public ResponseEntity<ApiResponse<PlaylistResponse>> create(@Valid @RequestBody CreatePlaylistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Playlist created successfully", playlistService.createPlaylist(request)));
    }

    @Operation(summary = "Current user's playlists")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PlaylistResponse>>> mine(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Playlists fetched successfully",
                playlistService.getMyPlaylists(pageable)));
    }

    @Operation(summary = "Search public playlists")
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<PageResponse<PlaylistResponse>>> publicPlaylists(
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Public playlists fetched successfully",
                playlistService.searchPublicPlaylists(query, pageable)));
    }

    @Operation(summary = "Playlist details with ordered tracks",
            description = "Private playlists are only readable by their owner")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaylistDetailResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Playlist fetched successfully", playlistService.getPlaylist(id)));
    }

    @Operation(summary = "Update playlist")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaylistResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody UpdatePlaylistRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Playlist updated successfully",
                playlistService.updatePlaylist(id, request)));
    }

    @Operation(summary = "Delete playlist")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        playlistService.deletePlaylist(id);
        return ResponseEntity.ok(ApiResponse.success("Playlist deleted successfully", null));
    }

    @Operation(summary = "Add a song", description = "Optional 0-based position; appended when omitted")
    @PostMapping("/{id}/songs")
    public ResponseEntity<ApiResponse<PlaylistDetailResponse>> addSong(@PathVariable UUID id,
                                                                     @Valid @RequestBody AddSongRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Song added to playlist", playlistService.addSong(id, request)));
    }

    @Operation(summary = "Remove a song")
    @DeleteMapping("/{id}/songs/{songId}")
    public ResponseEntity<ApiResponse<PlaylistDetailResponse>> removeSong(@PathVariable UUID id,
                                                                         @PathVariable UUID songId) {
        return ResponseEntity.ok(ApiResponse.success("Song removed from playlist",
                playlistService.removeSong(id, songId)));
    }

    @Operation(summary = "Reorder tracks", description = "Send the complete ordered list of song ids")
    @PutMapping("/{id}/songs")
    public ResponseEntity<ApiResponse<PlaylistDetailResponse>> reorder(@PathVariable UUID id,
                                                                      @Valid @RequestBody ReorderPlaylistRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Playlist reordered successfully",
                playlistService.reorder(id, request)));
    }

    @Operation(summary = "Remove every track")
    @DeleteMapping("/{id}/songs")
    public ResponseEntity<ApiResponse<PlaylistDetailResponse>> clear(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Playlist cleared", playlistService.clear(id)));
    }
}
