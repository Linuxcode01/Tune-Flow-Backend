package com.tuneflow.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuneflow.common.config.AppProperties;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.storage.dto.InitiateUploadRequest;
import com.tuneflow.storage.dto.InitiateUploadResponse;
import com.tuneflow.storage.dto.PartUploadResponse;
import com.tuneflow.storage.dto.UploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Chunked audio uploads (docs section 16).
 *
 * <p>Why this exists: a single 40 MB {@code multipart/form-data} POST over a mobile connection is a
 * bet against the network. If it fails at 90% the artist starts again from zero, and the client can
 * only show the progress Dio reports for one giant request. Chunking turns that into a sequence of
 * small, individually retryable requests with real progress, and the session is recorded on disk so
 * an interrupted upload can be resumed instead of restarted.
 *
 * <pre>
 * POST   /api/v1/uploads/audio/initiate                     -> { uploadId, chunkSizeBytes, partCount }
 * PUT    /api/v1/uploads/audio/{uploadId}/parts/{n}         -> acknowledge part n
 * GET    /api/v1/uploads/audio/{uploadId}                   -> receivedParts (resume)
 * POST   /api/v1/uploads/audio/{uploadId}/complete          -> UploadResponse (objectKey, duration…)
 * DELETE /api/v1/uploads/audio/{uploadId}                   -> discard the session
 * </pre>
 *
 * <p>The server dictates the chunk size and never trusts the client's arithmetic: part numbers are
 * range-checked, each part is capped at the negotiated chunk size, and completion is refused until
 * every part is present.
 *
 * <p>State lives on the API node's local disk ({@code tuneflow.media.chunk-temp-path}) and the parts
 * are assembled there before the finished object is handed to {@link StorageService}. A single node
 * is therefore required for the duration of one upload; the storage abstraction - and therefore S3 /
 * MinIO in production - is untouched. Moving session state to Redis or S3 multipart uploads is a
 * drop-in change behind this class if the API is ever scaled horizontally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkedUploadService {

    private static final String SESSION_FILE = "session.json";
    private static final String PART_PREFIX = "part-";
    private static final String PART_SUFFIX = ".bin";

    private final MediaUploadService mediaUploadService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private volatile Path root;

    // ---------------------------------------------------------------------------------------------
    // API
    // ---------------------------------------------------------------------------------------------

    /** Opens a session after validating the format and size the client declared. */
    public InitiateUploadResponse initiate(UUID ownerId, InitiateUploadRequest request) {
        AppProperties.Media media = appProperties.media();
        String extension = extensionOf(request.fileName());
        if (!media.allowedAudioFormats().contains(extension)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported audio format '" + extension + "'. Allowed: " + media.allowedAudioFormats());
        }
        if (request.sizeBytes() > media.maxAudioSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "Maximum audio size is " + (media.maxAudioSizeBytes() / (1024 * 1024)) + " MB");
        }

        int chunkSize = media.chunkSizeBytes();
        int partCount = (int) ((request.sizeBytes() + chunkSize - 1) / chunkSize);

        UUID uploadId = UUID.randomUUID();
        UploadSession session = new UploadSession(
                uploadId,
                ownerId,
                sanitize(request.fileName()),
                extension,
                request.contentType(),
                request.sizeBytes(),
                partCount,
                chunkSize,
                Instant.now());
        writeSession(session);
        log.info("Chunked upload {} opened by {} - {} ({} bytes, {} parts of {} bytes)",
                uploadId, ownerId, session.fileName(), session.sizeBytes(), partCount, chunkSize);
        return toResponse(session, List.of());
    }

    /** Current state of a session - the resume path. */
    public InitiateUploadResponse status(UUID ownerId, UUID uploadId) {
        UploadSession session = requireSession(ownerId, uploadId);
        return toResponse(session, receivedParts(session));
    }

    /**
     * Stores one part. Parts may arrive in any order and may be re-sent (a retry after a timeout
     * overwrites the previous attempt), which is what makes the client's retry loop safe.
     */
    public PartUploadResponse storePart(UUID ownerId, UUID uploadId, int partNumber, InputStream content) {
        UploadSession session = requireSession(ownerId, uploadId);
        if (partNumber < 1 || partNumber > session.partCount()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Part " + partNumber + " is out of range (1-" + session.partCount() + ")");
        }

        Path target = partFile(session.uploadId(), partNumber);
        Path temp = null;
        long written = 0;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), "part-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    written += read;
                    // Stop one byte past the ceiling: the client is told the exact chunk size in the
                    // initiate response, so a larger part means a buggy or hostile caller.
                    if (written > session.chunkSizeBytes()) {
                        throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                                "Part " + partNumber + " exceeds the negotiated chunk size of "
                                        + session.chunkSizeBytes() + " bytes");
                    }
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (ApiException ex) {
            cleanup(temp);
            throw ex;
        } catch (IOException ex) {
            cleanup(temp);
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Failed to store part " + partNumber + ": " + ex.getMessage());
        }

        long receivedBytes = receivedParts(session).stream()
                .mapToLong(received -> expectedPartSize(session, received))
                .sum();
        boolean complete = receivedParts(session).size() == session.partCount();
        log.debug("Chunked upload {}: part {}/{} stored ({} bytes)", uploadId, partNumber, session.partCount(), written);
        return new PartUploadResponse(partNumber, written, receivedBytes, session.sizeBytes(), complete);
    }

    /** Assembles the parts, then runs them through the same probe/transcode/store path as a single-shot upload. */
    public UploadResponse complete(UUID ownerId, UUID uploadId) {
        UploadSession session = requireSession(ownerId, uploadId);
        List<Integer> received = receivedParts(session);
        if (received.size() != session.partCount()) {
            List<Integer> missing = IntStream.rangeClosed(1, session.partCount())
                    .filter(part -> !received.contains(part))
                    .boxed()
                    .toList();
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Upload is incomplete; missing parts: " + missing);
        }

        Path assembled = null;
        try {
            assembled = Files.createTempFile(sessionRoot(session.uploadId()), "assembled-", "." + session.extension());
            try (OutputStream out = Files.newOutputStream(assembled)) {
                for (int part = 1; part <= session.partCount(); part++) {
                    Files.copy(partFile(session.uploadId(), part), out);
                }
            }
            if (Files.size(assembled) != session.sizeBytes()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Assembled size " + Files.size(assembled) + " does not match the declared "
                                + session.sizeBytes() + " bytes");
            }
            return mediaUploadService.storeAudioFile(assembled, session.extension(), session.contentType());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Failed to assemble the upload: " + ex.getMessage());
        } finally {
            cleanup(assembled);
            // The session is spent either way: a failed assembly cannot be resumed from these parts,
            // and leaving gigabytes of stale parts behind is worse than making the client start over.
            deleteSession(session.uploadId());
        }
    }

    public void abort(UUID ownerId, UUID uploadId) {
        requireSession(ownerId, uploadId);
        deleteSession(uploadId);
        log.info("Chunked upload {} aborted by {}", uploadId, ownerId);
    }

    /** Sessions that were opened and never finished (app killed, network gone) must not accumulate. */
    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT5M")
    public void purgeExpiredSessions() {
        Path base = root();
        if (!Files.isDirectory(base)) {
            return;
        }
        Duration ttl = appProperties.media().uploadSessionTtl();
        Instant cutoff = Instant.now().minus(ttl);
        int purged = 0;
        try (DirectoryStream<Path> sessions = Files.newDirectoryStream(base)) {
            for (Path dir : sessions) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Instant createdAt = readSession(dir.resolve(SESSION_FILE))
                        .map(UploadSession::createdAt)
                        .orElseGet(() -> createdFallback(dir));
                if (createdAt.isBefore(cutoff)) {
                    // Directory names are upload ids; anything else in there is not ours to delete.
                    try {
                        deleteSession(UUID.fromString(dir.getFileName().toString()));
                        purged++;
                    } catch (IllegalArgumentException notASession) {
                        log.debug("Ignoring unexpected directory in the upload temp root: {}", dir);
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Chunked upload purge failed: {}", ex.getMessage());
        }
        if (purged > 0) {
            log.info("Purged {} expired chunked upload session(s) older than {}", purged, ttl);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Session storage
    // ---------------------------------------------------------------------------------------------

    private UploadSession requireSession(UUID ownerId, UUID uploadId) {
        UploadSession session = readSession(sessionRoot(uploadId).resolve(SESSION_FILE))
                // A session owned by somebody else is reported as missing: the caller learns nothing
                // about uploads that are not theirs.
                .filter(candidate -> candidate.ownerId().equals(ownerId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Upload session not found or expired"));
        Instant expiresAt = session.createdAt().plus(appProperties.media().uploadSessionTtl());
        if (Instant.now().isAfter(expiresAt)) {
            deleteSession(uploadId);
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Upload session not found or expired");
        }
        return session;
    }

    private void writeSession(UploadSession session) {
        Path dir = sessionRoot(session.uploadId());
        try {
            Files.createDirectories(dir);
            objectMapper.writeValue(dir.resolve(SESSION_FILE).toFile(), session);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Unable to open an upload session: " + ex.getMessage());
        }
    }

    private Optional<UploadSession> readSession(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), UploadSession.class));
        } catch (IOException ex) {
            log.warn("Unreadable upload session {}: {}", file, ex.getMessage());
            return Optional.empty();
        }
    }

    private void deleteSession(UUID uploadId) {
        Path dir = sessionRoot(uploadId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(ChunkedUploadService::cleanup);
        } catch (IOException ex) {
            log.warn("Could not remove upload session {}: {}", uploadId, ex.getMessage());
        }
    }

    private List<Integer> receivedParts(UploadSession session) {
        List<Integer> parts = new ArrayList<>();
        for (int part = 1; part <= session.partCount(); part++) {
            if (Files.isRegularFile(partFile(session.uploadId(), part))) {
                parts.add(part);
            }
        }
        return parts;
    }

    private long expectedPartSize(UploadSession session, int partNumber) {
        // Every part is exactly the chunk size except the last, which holds the remainder.
        long consumed = (long) (partNumber - 1) * session.chunkSizeBytes();
        return Math.min(session.chunkSizeBytes(), session.sizeBytes() - consumed);
    }

    private Instant createdFallback(Path dir) {
        try {
            return Files.getLastModifiedTime(dir).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private InitiateUploadResponse toResponse(UploadSession session, List<Integer> received) {
        return new InitiateUploadResponse(
                session.uploadId(),
                session.chunkSizeBytes(),
                session.partCount(),
                session.sizeBytes(),
                received,
                session.createdAt().plus(appProperties.media().uploadSessionTtl()));
    }

    // ---------------------------------------------------------------------------------------------
    // Paths and helpers
    // ---------------------------------------------------------------------------------------------

    private Path root() {
        Path cached = root;
        if (cached == null) {
            cached = Paths.get(appProperties.media().chunkTempPath()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(cached);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to create the upload temp directory " + cached, ex);
            }
            root = cached;
        }
        return cached;
    }

    private Path sessionRoot(UUID uploadId) {
        Path dir = root().resolve(uploadId.toString()).normalize();
        if (!dir.startsWith(root())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Invalid upload id");
        }
        return dir;
    }

    private Path partFile(UUID uploadId, int partNumber) {
        return sessionRoot(uploadId).resolve(PART_PREFIX + String.format("%05d", partNumber) + PART_SUFFIX);
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Keeps the original name for diagnostics but strips anything path-shaped. */
    private static String sanitize(String fileName) {
        String name = fileName == null ? "audio" : fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isBlank() ? "audio" : name;
    }

    private static void cleanup(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    /** The on-disk session record. Jackson serialises it straight to {@code session.json}. */
    private record UploadSession(
            UUID uploadId,
            UUID ownerId,
            String fileName,
            String extension,
            String contentType,
            long sizeBytes,
            int partCount,
            int chunkSizeBytes,
            Instant createdAt
    ) {
    }
}
