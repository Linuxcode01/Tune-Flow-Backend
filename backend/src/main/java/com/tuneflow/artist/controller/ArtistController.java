package com.tuneflow.artist.controller;

import com.tuneflow.artist.dto.ArtistResponse;
import com.tuneflow.artist.dto.UpdateArtistRequest;
import com.tuneflow.artist.service.ArtistService;
import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

/** {@code /api/v1/artists} - artist profiles, popular artists and the follow graph (docs section 13). */
@Tag(name = "Artists", description = "Artist profiles and following")
@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @Operation(summary = "Paginated artist catalog", description = "Optional free-text query")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ArtistResponse>>> list(
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Artists fetched successfully",
                artistService.getArtists(query, pageable)));
    }

    @Operation(summary = "Popular artists", description = "Drives the home feed rail")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PageResponse<ArtistResponse>>> popular(
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Popular artists fetched successfully",
                artistService.getPopularArtists(pageable)));
    }

    @Operation(summary = "Artists the current user follows")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/following")
    public ResponseEntity<ApiResponse<PageResponse<ArtistResponse>>> following(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Followed artists fetched successfully",
                artistService.getFollowedArtists(pageable)));
    }

    @Operation(summary = "The current user's artist profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ArtistResponse>> myProfile() {
        return ResponseEntity.ok(ApiResponse.success("Artist profile fetched successfully",
                artistService.getMyArtistProfile()));
    }

    @Operation(summary = "Update the current user's artist profile")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ARTIST')")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ArtistResponse>> updateMyProfile(
            @Valid @RequestBody UpdateArtistRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Artist profile updated successfully",
                artistService.updateMyArtistProfile(request)));
    }

    @Operation(summary = "Artist details")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Artist fetched successfully", artistService.getArtist(id)));
    }

    @Operation(summary = "Update an artist profile", description = "Owner or ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ARTIST','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody UpdateArtistRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Artist updated successfully",
                artistService.updateArtist(id, request)));
    }

    @Operation(summary = "Follow an artist")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(@PathVariable UUID id) {
        artistService.followArtist(id);
        return ResponseEntity.ok(ApiResponse.success("Artist followed successfully", null));
    }

    @Operation(summary = "Unfollow an artist")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable UUID id) {
        artistService.unfollowArtist(id);
        return ResponseEntity.ok(ApiResponse.success("Artist unfollowed successfully", null));
    }
}
