package com.tuneflow.user.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.service.SongService;
import com.tuneflow.user.dto.ChangePasswordRequest;
import com.tuneflow.user.dto.UpdateProfileRequest;
import com.tuneflow.user.dto.UserResponse;
import com.tuneflow.user.service.UserService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/users/me} - the authenticated listener's profile (docs section 13). */
@Tag(name = "Users", description = "Current user profile and account settings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SongService songService;

    @Operation(summary = "Get current user profile")
    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", userService.getCurrentUser()));
    }

    @Operation(summary = "Update profile (partial update)")
    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", userService.updateProfile(request)));
    }

    @Operation(summary = "Change password", description = "Revokes all refresh tokens on success")
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));
    }

    @Operation(summary = "Deactivate account", description = "Soft-deletes the account and revokes all sessions")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deactivate() {
        userService.deactivateAccount();
        return ResponseEntity.ok(ApiResponse.success("Account deactivated", null));
    }

    @Operation(summary = "Liked songs", description = "The current user's library, most recently liked first")
    @GetMapping("/liked-songs")
    public ResponseEntity<ApiResponse<PageResponse<SongResponse>>> likedSongs(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Liked songs fetched successfully",
                songService.getLikedSongs(pageable)));
    }
}
