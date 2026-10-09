package com.tuneflow.analytics.service;

import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Playback analytics (docs sections 23, 35, 40).
 *
 * <p>Runs off the request path. Two rules keep counters accurate:
 *
 * <ol>
 *   <li>Authorizing a stream is <em>not</em> a play - those events carry
 *       {@code stage=stream-authorised} and are ignored here.</li>
 *   <li>A play is counted once per session: either the track completed, or the client marked the
 *       update as final (pause/skip/end) and at least {@value #MIN_SCROBBLE_SECONDS} seconds were
 *       played. Progress ticks in between do not inflate the counter.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackAnalyticsService {

    private static final int MIN_SCROBBLE_SECONDS = 30;
    private static final String STAGE_STREAM_AUTHORISED = "stream-authorised";

    private final HistoryService historyService;

    public void handle(DomainEvent event) {
        if (event.songId() == null) {
            return;
        }
        switch (event.type()) {
            case DomainEvent.SONG_COMPLETED -> countPlay(event, true);
            case DomainEvent.SONG_PLAYED -> countPlay(event, false);
            case DomainEvent.SONG_DOWNLOADED ->
                    log.debug("Download recorded for song {}", event.songId());
            default -> {
                // Other event types are handled by their own consumers.
            }
        }
    }

    private void countPlay(DomainEvent event, boolean completed) {
        Map<String, Object> payload = event.payload();

        if (STAGE_STREAM_AUTHORISED.equals(String.valueOf(payload.get("stage")))) {
            log.debug("Ignoring stream authorization event for song {}", event.songId());
            return;
        }

        int playedSeconds = intValue(payload.get("playedSeconds"));
        boolean finalUpdate = booleanValue(payload.get("finalUpdate"));

        boolean countable = completed || (finalUpdate && playedSeconds >= MIN_SCROBBLE_SECONDS);
        if (!countable) {
            return;
        }

        historyService.incrementPlayCount(event.songId());
        log.debug("Counted play for song {} (completed={}, playedSeconds={})",
                event.songId(), completed, playedSeconds);
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
