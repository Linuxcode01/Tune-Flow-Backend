package com.tuneflow.lyrics.service;

import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.lyrics.dto.LyricsResponse;
import com.tuneflow.lyrics.dto.UpsertLyricsRequest;
import com.tuneflow.lyrics.entity.Lyrics;
import com.tuneflow.lyrics.repository.LyricsRepository;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/** Lyrics management (docs sections 3, 27). Timestamps are parsed and validated on write. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LyricsServiceImpl implements LyricsService {

    private static final String DEFAULT_LANGUAGE = "en";

    private final LyricsRepository lyricsRepository;
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;

    @Override
    @Transactional(readOnly = true)
    public LyricsResponse getLyrics(UUID songId, String language) {
        songRepository.findById(songId)
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));

        String requested = StringUtils.hasText(language) ? language.trim().toLowerCase() : DEFAULT_LANGUAGE;
        Lyrics lyrics = lyricsRepository.findBySong_IdAndLanguage(songId, requested)
                .orElseGet(() -> lyricsRepository.findBySong_Id(songId).stream().findFirst()
                        .orElseThrow(() -> new ApiException(ErrorCode.LYRICS_NOT_FOUND,
                                "Lyrics are not available for this song yet")));
        return toResponse(lyrics);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableLanguages(UUID songId) {
        return lyricsRepository.findBySong_Id(songId).stream()
                .map(Lyrics::getLanguage)
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public LyricsResponse upsertLyrics(UUID songId, UpsertLyricsRequest request) {
        Song song = songRepository.findById(songId)
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));
        assertCanManage(song);

        String language = request.language().trim().toLowerCase();
        // If the caller says "synced" but the payload has no timestamps, that is a client bug worth
        // surfacing rather than storing unusable data.
        if (request.synced() && !LrcParser.looksLikeLrc(request.content())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Synced lyrics must contain LRC timestamps such as [00:12.50]");
        }

        Lyrics lyrics = lyricsRepository.findBySong_IdAndLanguage(songId, language)
                .orElseGet(() -> Lyrics.builder()
                        .song(song)
                        .language(language)
                        .build());
        lyrics.setContent(request.content());
        lyrics.setSynced(request.synced());
        lyrics.setSource("artist-upload");

        Lyrics saved = lyricsRepository.save(lyrics);
        log.info("Lyrics upserted for song {} [{}]", songId, language);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteLyrics(UUID songId, String language) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));
        assertCanManage(song);

        Lyrics lyrics = lyricsRepository.findBySong_IdAndLanguage(songId, language.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(ErrorCode.LYRICS_NOT_FOUND));
        lyricsRepository.delete(lyrics);
    }

    // ------------------------------------------------------------------------------------ helpers

    private void assertCanManage(Song song) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        UUID ownArtistId = SecurityUtils.currentUser()
                .flatMap(user -> artistRepository.findByUser_Id(user.id()))
                .map(Artist::getId)
                .orElse(null);
        if (ownArtistId == null || song.getArtist() == null || !ownArtistId.equals(song.getArtist().getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only manage lyrics for your own songs");
        }
    }

    private LyricsResponse toResponse(Lyrics lyrics) {
        return new LyricsResponse(
                lyrics.getId(),
                lyrics.getSong().getId(),
                lyrics.getLanguage(),
                lyrics.isSynced(),
                lyrics.getContent(),
                lyrics.isSynced() ? LrcParser.parse(lyrics.getContent()) : List.of());
    }
}
