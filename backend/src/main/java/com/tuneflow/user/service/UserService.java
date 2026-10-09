package com.tuneflow.user.service;

import com.tuneflow.user.dto.ChangePasswordRequest;
import com.tuneflow.user.dto.UpdateProfileRequest;
import com.tuneflow.user.dto.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getCurrentUser();

    UserResponse getById(UUID userId);

    UserResponse updateProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);

    /** Soft-deactivates the account and revokes every refresh token. */
    void deactivateAccount();
}
