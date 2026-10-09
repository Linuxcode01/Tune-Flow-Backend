package com.tuneflow.lyrics.service;

import com.tuneflow.lyrics.dto.LyricsResponse;
import com.tuneflow.lyrics.dto.UpsertLyricsRequest;

import java.util.List;
import java.util.UUID;

public interface LyricsService {

    LyricsResponse getLyrics(UUID songId, String language);

    List<String> getAvailableLanguages(UUID songId);

    LyricsResponse upsertLyrics(UUID songId, UpsertLyricsRequest request);

    void deleteLyrics(UUID songId, String language);
}
