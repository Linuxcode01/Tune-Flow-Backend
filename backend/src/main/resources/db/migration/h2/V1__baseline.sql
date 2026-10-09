-- =============================================================================================
-- TuneFlow baseline schema (H2, PostgreSQL compatibility mode) - used by the `dev` profile so a
-- developer can run the whole platform with no external services.
--
-- Mirrors db/migration/postgresql/V1__baseline.sql exactly; only the column types differ
-- (TEXT -> VARCHAR(1000000)) because H2 has no TEXT type.
-- =============================================================================================

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    name              VARCHAR(120)  NOT NULL,
    email             VARCHAR(190)  NOT NULL,
    password_hash     VARCHAR(200)  NOT NULL,
    profile_image_url VARCHAR(1000000),
    role              VARCHAR(20)   NOT NULL,
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    token_version     INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    version           BIGINT,
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX idx_users_role ON users (role);

CREATE TABLE artists (
    id                UUID PRIMARY KEY,
    user_id           UUID REFERENCES users (id),
    name              VARCHAR(160) NOT NULL,
    bio               VARCHAR(1000000),
    profile_image_url VARCHAR(1000000),
    country           VARCHAR(80),
    verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    follower_count    BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    version           BIGINT,
    CONSTRAINT uk_artists_user_id UNIQUE (user_id)
);
CREATE INDEX idx_artists_name ON artists (name);
CREATE INDEX idx_artists_verified ON artists (verified);

CREATE TABLE albums (
    id              UUID PRIMARY KEY,
    artist_id       UUID         NOT NULL REFERENCES artists (id),
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000000),
    cover_image_url VARCHAR(1000000),
    release_date    DATE,
    song_count      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    version         BIGINT
);
CREATE INDEX idx_albums_artist ON albums (artist_id);
CREATE INDEX idx_albums_release_date ON albums (release_date);
CREATE INDEX idx_albums_title ON albums (title);

CREATE TABLE genres (
    id              UUID PRIMARY KEY,
    name            VARCHAR(80) NOT NULL,
    slug            VARCHAR(80) NOT NULL,
    description     VARCHAR(1000000),
    cover_image_url VARCHAR(1000000),
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    version         BIGINT,
    CONSTRAINT uk_genres_slug UNIQUE (slug)
);
CREATE INDEX idx_genres_name ON genres (name);

CREATE TABLE songs (
    id                  UUID PRIMARY KEY,
    album_id            UUID REFERENCES albums (id),
    artist_id           UUID         NOT NULL REFERENCES artists (id),
    title               VARCHAR(200) NOT NULL,
    duration_seconds    INTEGER      NOT NULL DEFAULT 0,
    audio_url           VARCHAR(1000000),
    stream_manifest_url VARCHAR(1000000),
    cover_image_url     VARCHAR(1000000),
    track_number        INTEGER,
    play_count          BIGINT       NOT NULL DEFAULT 0,
    like_count          BIGINT       NOT NULL DEFAULT 0,
    download_count      BIGINT       NOT NULL DEFAULT 0,
    release_date        DATE,
    explicit_flag       BOOLEAN      NOT NULL DEFAULT FALSE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    version             BIGINT
);
CREATE INDEX idx_songs_album ON songs (album_id);
CREATE INDEX idx_songs_artist ON songs (artist_id);
CREATE INDEX idx_songs_title ON songs (title);
CREATE INDEX idx_songs_release_date ON songs (release_date);
CREATE INDEX idx_songs_play_count ON songs (play_count);

CREATE TABLE song_genres (
    song_id  UUID NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genres (id) ON DELETE CASCADE,
    CONSTRAINT pk_song_genres PRIMARY KEY (song_id, genre_id),
    CONSTRAINT uk_song_genres UNIQUE (song_id, genre_id)
);
CREATE INDEX idx_song_genres_genre ON song_genres (genre_id);

CREATE TABLE playlists (
    id                     UUID PRIMARY KEY,
    user_id                UUID         NOT NULL REFERENCES users (id),
    name                   VARCHAR(200) NOT NULL,
    description            VARCHAR(1000000),
    cover_image_url        VARCHAR(1000000),
    is_public              BOOLEAN      NOT NULL DEFAULT FALSE,
    is_system              BOOLEAN      NOT NULL DEFAULT FALSE,
    song_count             INTEGER      NOT NULL DEFAULT 0,
    total_duration_seconds INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP,
    version                BIGINT
);
CREATE INDEX idx_playlists_user ON playlists (user_id);
CREATE INDEX idx_playlists_public ON playlists (is_public);
CREATE INDEX idx_playlists_name ON playlists (name);

CREATE TABLE playlist_songs (
    playlist_id UUID    NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    song_id     UUID    NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    added_by    VARCHAR(190),
    added_at    TIMESTAMP,
    CONSTRAINT pk_playlist_songs PRIMARY KEY (playlist_id, song_id)
);
CREATE INDEX idx_playlist_songs_playlist_position ON playlist_songs (playlist_id, position);
CREATE INDEX idx_playlist_songs_song ON playlist_songs (song_id);

CREATE TABLE liked_songs (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    song_id    UUID NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    created_at TIMESTAMP,
    CONSTRAINT pk_liked_songs PRIMARY KEY (user_id, song_id)
);
CREATE INDEX idx_liked_songs_user ON liked_songs (user_id);
CREATE INDEX idx_liked_songs_song ON liked_songs (song_id);

CREATE TABLE followed_artists (
    user_id             UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    artist_id           UUID    NOT NULL REFERENCES artists (id) ON DELETE CASCADE,
    notify_new_releases BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP,
    CONSTRAINT pk_followed_artists PRIMARY KEY (user_id, artist_id)
);
CREATE INDEX idx_followed_artists_user ON followed_artists (user_id);
CREATE INDEX idx_followed_artists_artist ON followed_artists (artist_id);

CREATE TABLE listening_history (
    id               UUID PRIMARY KEY,
    user_id          UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    song_id          UUID      NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    started_at       TIMESTAMP NOT NULL,
    completed_at     TIMESTAMP,
    played_seconds   INTEGER   NOT NULL DEFAULT 0,
    completion_ratio DOUBLE PRECISION,
    device_id        VARCHAR(120),
    offline_playback BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    version          BIGINT
);
CREATE INDEX idx_history_user_started ON listening_history (user_id, started_at);
CREATE INDEX idx_history_song ON listening_history (song_id);

CREATE TABLE lyrics (
    id         UUID PRIMARY KEY,
    song_id    UUID        NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    language   VARCHAR(10) NOT NULL DEFAULT 'en',
    content    VARCHAR(1000000)        NOT NULL,
    synced     BOOLEAN     NOT NULL DEFAULT FALSE,
    source     VARCHAR(200),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version    BIGINT,
    CONSTRAINT uk_lyrics_song_language UNIQUE (song_id, language)
);
CREATE INDEX idx_lyrics_song ON lyrics (song_id);

CREATE TABLE downloads (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    song_id            UUID         NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    device_id          VARCHAR(120) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    bitrate_kbps       INTEGER,
    size_bytes         BIGINT,
    downloaded_at      TIMESTAMP,
    licence_expires_at TIMESTAMP,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    version            BIGINT,
    CONSTRAINT uk_downloads_user_song_device UNIQUE (user_id, song_id, device_id)
);
CREATE INDEX idx_downloads_user ON downloads (user_id);
CREATE INDEX idx_downloads_status ON downloads (status);

CREATE TABLE refresh_tokens (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64) NOT NULL,
    expires_at       TIMESTAMP   NOT NULL,
    revoked_at       TIMESTAMP,
    replaced_by_hash VARCHAR(64),
    device_id        VARCHAR(120),
    user_agent       VARCHAR(300),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    version          BIGINT,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(1000000),
    data       VARCHAR(1000000),
    read_at    TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    version    BIGINT
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_unread ON notifications (user_id, read_at);
