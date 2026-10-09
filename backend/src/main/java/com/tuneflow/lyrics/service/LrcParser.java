package com.tuneflow.lyrics.service;

import com.tuneflow.lyrics.dto.LyricsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC-format lyrics ({@code [mm:ss.xx] text}) into timestamped lines.
 *
 * <p>Supports the common variants: {@code [mm:ss]}, {@code [mm:ss.xx]} and multiple timestamps on
 * one line ({@code [00:10.00][00:20.00]repeated chorus}), which is how LRC encodes a repeated hook.
 */
final class LrcParser {

    private static final Pattern LINE_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private LrcParser() {
    }

    static List<LyricsResponse.SyncedLine> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<LyricsResponse.SyncedLine> lines = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            Matcher matcher = LINE_PATTERN.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                long fraction = 0;
                if (matcher.group(3) != null) {
                    String frac = matcher.group(3);
                    // [mm:ss.x] -> tenths, [mm:ss.xx] -> hundredths, [mm:ss.xxx] -> milliseconds
                    fraction = switch (frac.length()) {
                        case 1 -> Long.parseLong(frac) * 100;
                        case 2 -> Long.parseLong(frac) * 10;
                        default -> Long.parseLong(frac);
                    };
                }
                timestamps.add(((minutes * 60) + seconds) * 1000 + fraction);
                textStart = matcher.end();
            }
            String text = rawLine.substring(Math.min(textStart, rawLine.length())).trim();
            for (Long timestamp : timestamps) {
                lines.add(new LyricsResponse.SyncedLine(timestamp, text));
            }
        }
        lines.sort(java.util.Comparator.comparingLong(LyricsResponse.SyncedLine::timeMs));
        return lines;
    }

    static boolean looksLikeLrc(String content) {
        return content != null && LINE_PATTERN.matcher(content).find();
    }
}
