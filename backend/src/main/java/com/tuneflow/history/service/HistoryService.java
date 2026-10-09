package com.tuneflow.history.service;

import com.tuneflow.common.response.PageResponse;
import com.tuneflow.history.dto.HistoryEntryResponse;
import com.tuneflow.history.dto.HistoryStatsResponse;
import com.tuneflow.history.dto.RecordPlaybackRequest;
import com.tuneflow.music.dto.SongResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface HistoryService {

    HistoryEntryResponse recordPlayback(RecordPlaybackRequest request);

    PageResponse<HistoryEntryResponse> getHistory(Pageable pageable);

    /** Distinct songs, most recent play first - the "Recently played" rail. */
    List<SongResponse> getRecentlyPlayed(int limit);

    void clearHistory();

    /** Aggregated listening statistics for the current user (docs section 13). */
    HistoryStatsResponse getStats();

    /** Called by the asynchronous analytics consumer to keep counters warm. */
    void incrementPlayCount(UUID songId);
}
