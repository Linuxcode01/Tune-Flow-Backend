package com.tuneflow.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tuneflow.common.config.AppProperties;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.storage.dto.InitiateUploadRequest;
import com.tuneflow.storage.dto.InitiateUploadResponse;
import com.tuneflow.storage.dto.PartUploadResponse;
import com.tuneflow.storage.dto.UploadResponse;
import com.tuneflow.storage.service.ChunkedUploadService;
import com.tuneflow.storage.service.MediaUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chunked upload behaviour, without a servlet container: the session bookkeeping is plain file I/O,
 * and the final store is a collaborator we stub (its own behaviour is covered elsewhere).
 */
class ChunkedUploadServiceTest {

    /** 64 KB is the smallest chunk size the configuration accepts. */
    private static final int CHUNK = 64 * 1024;
    private static final int PAYLOAD_BYTES = 150_000; // 2 full parts + a remainder

    @TempDir
    Path tempDir;

    private MediaUploadService mediaUploadService;
    private java.util.concurrent.atomic.AtomicReference<byte[]> storedBytes;
    private ChunkedUploadService service;
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AppProperties.Media media = new AppProperties.Media(
                Duration.ofMinutes(10),
                128,
                5L * 1024 * 1024,   // 5 MB ceiling keeps the "too large" case cheap to express
                1024L * 1024L,
                List.of("mp3", "wav"),
                List.of("jpg"),
                CHUNK,
                Duration.ofHours(24),
                tempDir.toString());

        AppProperties properties = mock(AppProperties.class);
        when(properties.media()).thenReturn(media);

        mediaUploadService = mock(MediaUploadService.class);
        storedBytes = new java.util.concurrent.atomic.AtomicReference<>();
        when(mediaUploadService.storeAudioFile(any(), anyString(), any())).thenAnswer(invocation -> {
            // Read inside the call: complete() removes the session directory (and the assembled temp
            // file inside it) before it returns.
            storedBytes.set(Files.readAllBytes(invocation.getArgument(0)));
            return new UploadResponse("audio/2026/10/stored.mp3", "/api/v1/media/stored.mp3",
                    600, 12, 128, "audio/mpeg", 4, "checksum");
        });

        service = new ChunkedUploadService(
                mediaUploadService, properties, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    @DisplayName("initiate splits the declared size into parts the client is told to send")
    void initiatePlansParts() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("neon-skyline.mp3", (long) PAYLOAD_BYTES, "audio/mpeg"));

        assertThat(session.chunkSizeBytes()).isEqualTo(CHUNK);
        // 1024 + 1024 + 452: the last part is the remainder, and it is still a valid part.
        assertThat(session.partCount()).isEqualTo(3);
        assertThat(session.receivedParts()).isEmpty();
        assertThat(session.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("initiate rejects an unsupported format and an oversized file before any bytes move")
    void initiateValidatesBeforeTransfer() {
        assertThatThrownBy(() -> service.initiate(ownerId,
                new InitiateUploadRequest("installer.exe", 1024L, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        assertThatThrownBy(() -> service.initiate(ownerId,
                new InitiateUploadRequest("huge.mp3", 50L * 1024L * 1024L, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("parts may arrive out of order and are reported back as received")
    void partsArriveInAnyOrder() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.wav", (long) PAYLOAD_BYTES, "audio/wav"));
        int remainder = PAYLOAD_BYTES - 2 * CHUNK;

        PartUploadResponse third = part(session, 3, remainder);
        assertThat(third.partSizeBytes()).isEqualTo(remainder);
        assertThat(third.complete()).isFalse();

        PartUploadResponse first = part(session, 1, CHUNK);
        assertThat(first.receivedBytes()).isEqualTo(CHUNK + (long) remainder);

        assertThat(service.status(ownerId, session.uploadId()).receivedParts()).containsExactly(1, 3);

        part(session, 2, CHUNK);
        assertThat(service.status(ownerId, session.uploadId()).receivedParts()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("complete assembles the parts byte-for-byte, in order, and stores the object once")
    void completeAssemblesParts() throws IOException {
        byte[] bytes = payload(PAYLOAD_BYTES);
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.mp3", (long) bytes.length, "audio/mpeg"));

        // Send in a deliberately awkward order: correctness must not depend on arrival order.
        sendSlice(session, bytes, 2);
        sendSlice(session, bytes, 1);
        sendSlice(session, bytes, 3);

        UploadResponse stored = service.complete(ownerId, session.uploadId());

        assertThat(stored.objectKey()).isEqualTo("audio/2026/10/stored.mp3");
        verify(mediaUploadService).storeAudioFile(any(), org.mockito.ArgumentMatchers.eq("mp3"),
                org.mockito.ArgumentMatchers.eq("audio/mpeg"));

        // The bytes handed to storage are the original file, reassembled in the right order.
        assertThat(storedBytes.get()).isEqualTo(bytes);

        // The session is spent: its temp directory (parts included) is gone.
        assertThat(Files.exists(tempDir.resolve(session.uploadId().toString()))).isFalse();
    }

    @Test
    @DisplayName("complete refuses an incomplete upload and says which parts are missing")
    void completeRequiresEveryPart() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.mp3", (long) PAYLOAD_BYTES, null));
        part(session, 1, CHUNK);

        assertThatThrownBy(() -> service.complete(ownerId, session.uploadId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("[2, 3]");
    }

    @Test
    @DisplayName("a part outside the negotiated range is rejected")
    void partNumbersAreRangeChecked() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.mp3", (long) PAYLOAD_BYTES, null));

        assertThatThrownBy(() -> part(session, 4, 10))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("a part larger than the negotiated chunk size is rejected rather than written")
    void oversizedPartIsRejected() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.mp3", (long) PAYLOAD_BYTES, null));

        assertThatThrownBy(() -> part(session, 1, CHUNK + 1))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("another artist's session is invisible, not merely forbidden")
    void sessionsAreScopedToTheirOwner() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.mp3", (long) PAYLOAD_BYTES, null));

        UUID intruder = UUID.randomUUID();
        assertThatThrownBy(() -> service.status(intruder, session.uploadId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> part(intruder, session.uploadId(), 1, CHUNK))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("abort discards the session and its parts")
    void abortRemovesEverything() {
        InitiateUploadResponse session = service.initiate(ownerId,
                new InitiateUploadRequest("track.mp3", (long) PAYLOAD_BYTES, null));
        part(session, 1, CHUNK);

        service.abort(ownerId, session.uploadId());

        assertThat(Files.exists(tempDir.resolve(session.uploadId().toString()))).isFalse();
        assertThatThrownBy(() -> service.status(ownerId, session.uploadId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("the purge sweep removes stale sessions and leaves live ones alone")
    void purgeRemovesOnlyExpiredSessions() {
        InitiateUploadResponse live = service.initiate(ownerId,
                new InitiateUploadRequest("live.mp3", (long) PAYLOAD_BYTES, null));

        // A session directory that looks a week old, which is past the 24 h TTL.
        Path stale = tempDir.resolve(UUID.randomUUID().toString());
        Path staleSession = stale.resolve("session.json");
        writeStaleSession(staleSession);

        service.purgeExpiredSessions();

        assertThat(Files.exists(stale)).isFalse();
        assertThat(Files.exists(tempDir.resolve(live.uploadId().toString()))).isTrue();
    }

    // ---------------------------------------------------------------------------------------------

    private PartUploadResponse part(InitiateUploadResponse session, int partNumber, int size) {
        return part(ownerId, session.uploadId(), partNumber, size);
    }

    private PartUploadResponse part(UUID asUser, UUID uploadId, int partNumber, int size) {
        return service.storePart(asUser, uploadId, partNumber, new ByteArrayInputStream(payload(size)));
    }

    private void sendSlice(InitiateUploadResponse session, byte[] bytes, int partNumber) {
        int start = (partNumber - 1) * CHUNK;
        int end = Math.min(bytes.length, start + CHUNK);
        byte[] slice = java.util.Arrays.copyOfRange(bytes, start, end);
        service.storePart(ownerId, session.uploadId(), partNumber, new ByteArrayInputStream(slice));
    }

    private static byte[] payload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    private void writeStaleSession(Path sessionFile) {
        try {
            Files.createDirectories(sessionFile.getParent());
            String json = """
                    {
                      "uploadId": "%s",
                      "ownerId": "%s",
                      "fileName": "old.mp3",
                      "extension": "mp3",
                      "contentType": null,
                      "sizeBytes": 2048,
                      "partCount": 2,
                      "chunkSizeBytes": 1024,
                      "createdAt": "2020-01-01T00:00:00Z"
                    }
                    """.formatted(sessionFile.getParent().getFileName(), UUID.randomUUID());
            Files.writeString(sessionFile, json);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
