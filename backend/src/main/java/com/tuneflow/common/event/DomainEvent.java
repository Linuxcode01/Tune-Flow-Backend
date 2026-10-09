package com.tuneflow.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event published asynchronously (docs section 23). The API request path never blocks on
 * analytics: it writes the event to Kafka (when enabled) or dispatches it to local listeners.
 *
 * <p>Event types: {@code song.played}, {@code song.completed}, {@code song.liked},
 * {@code playlist.created}, {@code playlist.updated}, {@code user.followed},
 * {@code song.downloaded}.
 */
public record DomainEvent(
        String type,
        UUID userId,
        UUID songId,
        UUID playlistId,
        UUID artistId,
        Map<String, Object> payload,
        Instant occurredAt
) {

    public static final String SONG_PLAYED = "song.played";
    public static final String SONG_COMPLETED = "song.completed";
    public static final String SONG_LIKED = "song.liked";
    public static final String SONG_UNLIKED = "song.unliked";
    public static final String PLAYLIST_CREATED = "playlist.created";
    public static final String PLAYLIST_UPDATED = "playlist.updated";
    public static final String USER_FOLLOWED = "user.followed";
    public static final String SONG_DOWNLOADED = "song.downloaded";

    public static Builder builder(String type) {
        return new Builder(type);
    }

    public static final class Builder {
        private String type;
        private UUID userId;
        private UUID songId;
        private UUID playlistId;
        private UUID artistId;
        private Map<String, Object> payload = Map.of();
        private Instant occurredAt = Instant.now();

        private Builder(String type) {
            this.type = type;
        }

        public Builder user(UUID value) {
            this.userId = value;
            return this;
        }

        public Builder song(UUID value) {
            this.songId = value;
            return this;
        }

        public Builder playlist(UUID value) {
            this.playlistId = value;
            return this;
        }

        public Builder artist(UUID value) {
            this.artistId = value;
            return this;
        }

        public Builder payload(Map<String, Object> value) {
            this.payload = value == null ? Map.of() : value;
            return this;
        }

        public Builder at(Instant value) {
            this.occurredAt = value;
            return this;
        }

        public DomainEvent build() {
            return new DomainEvent(type, userId, songId, playlistId, artistId, payload, occurredAt);
        }
    }
}
