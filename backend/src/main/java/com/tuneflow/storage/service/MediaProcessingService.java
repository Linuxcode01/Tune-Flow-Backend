package com.tuneflow.storage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg-backed media pipeline (docs section 16).
 *
 * <p>Responsibilities: validate the uploaded media, extract metadata (duration/bitrate), transcode
 * to a normalized MP3 rendition and - when enabled - package HLS segments for adaptive streaming.
 *
 * <p>FFmpeg is an optional runtime dependency: if the binary is missing, the pipeline degrades
 * gracefully and stores the original upload, so a developer without FFmpeg installed can still run
 * the platform end to end. Callers can detect this through {@link #isAvailable()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private static final int PROCESS_TIMEOUT_SECONDS = 120;

    private final ObjectMapper objectMapper;

    @Value("${tuneflow.media.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${tuneflow.media.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${tuneflow.media.hls-enabled:false}")
    private boolean hlsEnabled;

    /** Cached capability check - probing the binary on every upload would be wasteful. */
    private volatile Boolean available;

    public boolean isAvailable() {
        Boolean cached = available;
        if (cached == null) {
            cached = canRun(ffmpegPath, "-version") && canRun(ffprobePath, "-version");
            available = cached;
            if (!cached) {
                log.warn("FFmpeg/FFprobe not found on PATH - uploads will be stored without transcoding");
            }
        }
        return cached;
    }

    public MediaMetadata probe(Path file) {
        if (!isAvailable()) {
            return new MediaMetadata(durationSeconds(file), null, null);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    ffprobePath, "-v", "quiet", "-print_format", "json",
                    "-show_format", "-show_streams", file.toAbsolutePath().toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                log.warn("ffprobe failed for {} (exit {})", file, process.exitValue());
                return new MediaMetadata(0, null, null);
            }
            JsonNode root = objectMapper.readTree(output);
            JsonNode format = root.path("format");
            double duration = format.path("duration").asDouble(0);
            long bitrate = format.path("bit_rate").asLong(0);
            String codec = root.path("streams").isEmpty()
                    ? null
                    : root.path("streams").get(0).path("codec_name").asText(null);
            String formatName = format.path("format_name").asText(null);
            return new MediaMetadata(
                    (int) Math.round(duration),
                    bitrate > 0 ? (int) (bitrate / 1000) : null,
                    codec != null ? codec : formatName);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Media probe failed for {}: {}", file, ex.getMessage());
            return new MediaMetadata(0, null, null);
        }
    }

    /**
     * Transcodes the source into a normalized MP3 rendition.
     *
     * @return the produced file, or the original when FFmpeg is unavailable
     */
    public Path transcodeToMp3(Path source, Path targetDir, int bitrateKbps) {
        if (!isAvailable()) {
            return source;
        }
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(stripExtension(source.getFileName().toString()) + ".mp3");
            List<String> command = List.of(ffmpegPath, "-y", "-i", source.toAbsolutePath().toString(),
                    "-vn", "-codec:a", "libmp3lame", "-b:a", bitrateKbps + "k",
                    "-ar", "44100", target.toAbsolutePath().toString());
            run(command, "transcode");
            return target;
        } catch (IOException | RuntimeException ex) {
            log.warn("Transcode failed, falling back to the original upload: {}", ex.getMessage());
            return source;
        }
    }

    /**
     * Packages HLS segments ({@code index.m3u8} + {@code *.ts}) for adaptive streaming.
     * Returns the playlist path, or empty when HLS is disabled or FFmpeg is unavailable.
     */
    public java.util.Optional<Path> packageHls(Path source, Path targetDir) {
        if (!hlsEnabled || !isAvailable()) {
            return java.util.Optional.empty();
        }
        try {
            Files.createDirectories(targetDir);
            Path playlist = targetDir.resolve("index.m3u8");
            List<String> command = new ArrayList<>(List.of(
                    ffmpegPath, "-y", "-i", source.toAbsolutePath().toString(),
                    "-codec:a", "aac", "-b:a", "128k", "-ac", "2",
                    "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod",
                    "-hls_segment_filename", targetDir.resolve("segment_%03d.ts").toAbsolutePath().toString(),
                    playlist.toAbsolutePath().toString()));
            run(command, "hls packaging");
            return java.util.Optional.of(playlist);
        } catch (IOException | RuntimeException ex) {
            log.warn("HLS packaging failed: {}", ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    private void run(List<String> command, String operation) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ApiException(ErrorCode.MEDIA_PROCESSING_FAILED, "Media " + operation + " timed out");
            }
            if (process.exitValue() != 0) {
                log.warn("ffmpeg {} exited with {}: {}", operation, process.exitValue(), tail(output));
                throw new ApiException(ErrorCode.MEDIA_PROCESSING_FAILED, "Media " + operation + " failed");
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.MEDIA_PROCESSING_FAILED, "Unable to run FFmpeg: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.MEDIA_PROCESSING_FAILED, "Media processing interrupted");
        }
    }

    private boolean canRun(String binary, String argument) {
        try {
            Process process = new ProcessBuilder(binary, argument).redirectErrorStream(true).start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int durationSeconds(Path file) {
        // Without FFprobe we cannot read the real duration; the client reports the measured value
        // back with the first playback event, and an artist can correct the metadata explicitly.
        return 0;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String tail(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= 400 ? trimmed : trimmed.substring(trimmed.length() - 400);
    }

    public record MediaMetadata(int durationSeconds, Integer bitrateKbps, String codec) {
    }
}
