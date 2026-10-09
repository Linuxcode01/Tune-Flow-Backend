package com.tuneflow.lyrics.service;

import com.tuneflow.lyrics.dto.LyricsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Synchronized-lyrics parsing (docs section 27). */
class LrcParserTest {

    @Test
    @DisplayName("parses [mm:ss.xx] timestamps into milliseconds")
    void parsesHundredths() {
        List<LyricsResponse.SyncedLine> lines = LrcParser.parse("[00:12.40]Neon skyline\n[01:05.05]City hums");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).timeMs()).isEqualTo(12_400);
        assertThat(lines.get(0).text()).isEqualTo("Neon skyline");
        assertThat(lines.get(1).timeMs()).isEqualTo(65_050);
    }

    @Test
    @DisplayName("supports whole-second, tenth and millisecond precision")
    void supportsPrecisionVariants() {
        assertThat(LrcParser.parse("[00:07]Line").get(0).timeMs()).isEqualTo(7_000);
        assertThat(LrcParser.parse("[00:07.5]Line").get(0).timeMs()).isEqualTo(7_500);
        assertThat(LrcParser.parse("[00:07.123]Line").get(0).timeMs()).isEqualTo(7_123);
    }

    @Test
    @DisplayName("expands repeated hooks declared with multiple timestamps")
    void expandsMultipleTimestampsOnOneLine() {
        List<LyricsResponse.SyncedLine> lines = LrcParser.parse("[00:18.10][00:42.00]City hums in harmony");

        assertThat(lines).hasSize(2);
        assertThat(lines).allMatch(line -> line.text().equals("City hums in harmony"));
        assertThat(lines).extracting(LyricsResponse.SyncedLine::timeMs).containsExactly(18_100L, 42_000L);
    }

    @Test
    @DisplayName("output is sorted by time regardless of source order")
    void sortsByTimestamp() {
        List<LyricsResponse.SyncedLine> lines = LrcParser.parse("[00:30.00]Second\n[00:10.00]First");

        assertThat(lines).extracting(LyricsResponse.SyncedLine::text).containsExactly("First", "Second");
        assertThat(lines.get(0).timeMs()).isLessThan(lines.get(1).timeMs());
    }

    @Test
    @DisplayName("detects whether a payload looks like LRC at all")
    void detectsLrc() {
        assertThat(LrcParser.looksLikeLrc("[00:01.00]Hi")).isTrue();
        assertThat(LrcParser.looksLikeLrc("just plain text")).isFalse();
        assertThat(LrcParser.looksLikeLrc(null)).isFalse();
    }
}
