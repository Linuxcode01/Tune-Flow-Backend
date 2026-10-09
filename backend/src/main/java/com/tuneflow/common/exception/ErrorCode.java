package com.tuneflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Canonical machine-readable error codes returned in {@code errorCode} of every error response.
 * The Flutter client switches on these strings instead of parsing human-readable messages.
 */
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "Invalid or malformed token"),

    // 401
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "Artist not found"),
    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND, "Album not found"),
    SONG_NOT_FOUND(HttpStatus.NOT_FOUND, "Song not found"),
    GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "Genre not found"),
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "Playlist not found"),
    LYRICS_NOT_FOUND(HttpStatus.NOT_FOUND, "Lyrics not found"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    // 409
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account with this email already exists"),
    ALREADY_LIKED(HttpStatus.CONFLICT, "Song is already liked"),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "Artist is already followed"),
    SONG_ALREADY_IN_PLAYLIST(HttpStatus.CONFLICT, "Song is already part of this playlist"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),

    // 413 / 415
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the allowed size"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),

    // 429
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please slow down"),

    // 500 / 502 / 503
    MEDIA_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Media processing failed"),
    STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "Object storage is unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
