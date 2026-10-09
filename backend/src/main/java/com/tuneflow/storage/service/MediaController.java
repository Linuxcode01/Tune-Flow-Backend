package com.tuneflow.storage.service;

import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves locally stored media <em>only</em> against a valid, unexpired signature
 * ({@code ?expires=&signature=}) - the local-storage equivalent of an S3 pre-signed URL.
 *
 * <p>Byte-range requests are supported so the Flutter player can seek without downloading the whole
 * track first (HTTP 206 + {@code Content-Range}). The response is written directly to the servlet
 * output stream in 64 KB chunks: no whole-file buffering, and no conflict with Spring's message
 * converters, which cannot negotiate a Content-Type for partial resources. In production this
 * endpoint is replaced entirely by the CDN/object store (docs sections 17, 33).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnProperty(name = "tuneflow.storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class MediaController {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final LocalStorageService localStorageService;

    @GetMapping("/{*objectKey}")
    public void stream(@PathVariable String objectKey,
                       @RequestParam("expires") long expires,
                       @RequestParam("signature") String signature,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {

        String normalizedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;

        localStorageService.validateSignedRequest(normalizedKey, expires, signature)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Media link is invalid or has expired"));

        Path file = localStorageService.resolve(normalizedKey);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Media object not found");
        }

        long total = Files.size(file);
        ByteRange range = ByteRange.parse(request.getHeader(HttpHeaders.RANGE), total);

        response.setStatus(range.partial() ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        response.setContentType(mediaTypeFor(file).toString());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setContentLengthLong(range.length());
        if (range.partial()) {
            response.setHeader(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + range.end() + "/" + total);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(range.start());
            OutputStream out = response.getOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = range.length();
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
            out.flush();
        } catch (IOException ex) {
            // Client aborted the stream (player skipped/closed): expected, not an error worth alerting.
            log.debug("Media stream interrupted for {}: {}", normalizedKey, ex.getMessage());
        }
    }

    /** Inclusive byte range with a hard 1 MB cap per response so a single request cannot hog a thread. */
    private record ByteRange(long start, long end, boolean partial) {

        private static final long MAX_CHUNK = 1024 * 1024;

        long length() {
            return end - start + 1;
        }

        static ByteRange parse(String header, long total) {
            if (header == null || !header.startsWith("bytes=")) {
                return new ByteRange(0, total - 1, false);
            }
            String value = header.substring("bytes=".length()).trim();
            String[] parts = value.split("-", 2);
            try {
                long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
                long end = parts.length < 2 || parts[1].isEmpty()
                        ? total - 1
                        : Math.min(Long.parseLong(parts[1]), total - 1);
                if (start < 0 || start >= total || end < start) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST, "Requested byte range is not satisfiable");
                }
                end = Math.min(end, start + MAX_CHUNK - 1);
                return new ByteRange(start, end, true);
            } catch (NumberFormatException ex) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Malformed Range header");
            }
        }
    }

    private static MediaType mediaTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (name.endsWith(".m4a") || name.endsWith(".aac")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        if (name.endsWith(".flac")) {
            return MediaType.parseMediaType("audio/flac");
        }
        if (name.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (name.endsWith(".ogg")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (name.endsWith(".m3u8")) {
            return MediaType.parseMediaType("application/vnd.apple.mpegurl");
        }
        if (name.endsWith(".ts")) {
            return MediaType.parseMediaType("video/mp2t");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
