package com.tuneflow.streaming.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stream descriptor must describe the bytes it points at.
 *
 * <p>A hard-coded {@code audio/mpeg} makes players refuse perfectly valid wav/flac/m4a uploads, which
 * is exactly the "the song uploaded but won't play" symptom artists were hitting.
 */
class StreamingContentTypeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "audio/2026/10/abc.mp3,  audio/mpeg",
            "audio/2026/10/abc.MP3,  audio/mpeg",
            "audio/2026/10/abc.wav,  audio/wav",
            "audio/2026/10/abc.m4a,  audio/mp4",
            "audio/2026/10/abc.aac,  audio/mp4",
            "audio/2026/10/abc.flac, audio/flac",
            "audio/2026/10/abc.ogg,  audio/ogg",
            "audio/2026/10/abc.opus, audio/ogg",
            "audio/2026/10/abc,      application/octet-stream"
    })
    @DisplayName("content type follows the stored object's extension")
    void contentTypeFollowsExtension(String objectKey, String expected) {
        assertThat(StreamingServiceImpl.contentTypeFor(objectKey, false)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an HLS playlist always reports the manifest type")
    void hlsWins() {
        assertThat(StreamingServiceImpl.contentTypeFor("hls/song/master.m3u8", true))
                .isEqualTo("application/vnd.apple.mpegurl");
        assertThat(StreamingServiceImpl.contentTypeFor("hls/song/master.m3u8", false))
                .isEqualTo("application/vnd.apple.mpegurl");
    }
}
