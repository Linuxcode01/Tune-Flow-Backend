package com.tuneflow.music.service;

import com.tuneflow.music.dto.AlbumSummary;
import com.tuneflow.music.dto.ArtistSummary;
import com.tuneflow.music.dto.GenreResponse;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.Song;
import com.tuneflow.storage.service.ArtworkUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Maps song/album/artist entities to API DTOs, including signed artwork URLs. */
@Component
@RequiredArgsConstructor
public class SongMapper {

    private final ArtworkUrlResolver artworkUrlResolver;

    /**
     * @param liked null when the caller is anonymous, otherwise whether the song is in the user's library
     */
    public SongResponse toResponse(Song song, Boolean liked) {
        if (song == null) {
            return null;
        }
        ArtistSummary artist = song.getArtist() == null ? null : new ArtistSummary(
                song.getArtist().getId(),
                song.getArtist().getName(),
                artworkUrlResolver.resolve(song.getArtist().getProfileImageUrl()),
                song.getArtist().isVerified());

        AlbumSummary album = song.getAlbum() == null ? null : new AlbumSummary(
                song.getAlbum().getId(),
                song.getAlbum().getTitle(),
                artworkUrlResolver.resolve(song.getAlbum().getCoverImageUrl()),
                song.getAlbum().getReleaseDate());

        String cover = song.getCoverImageUrl() != null
                ? artworkUrlResolver.resolve(song.getCoverImageUrl())
                : (song.getAlbum() != null ? artworkUrlResolver.resolve(song.getAlbum().getCoverImageUrl()) : null);

        List<GenreResponse> genres = song.getGenres() == null
                ? List.of()
                : song.getGenres().stream().map(this::toGenreResponse).toList();

        return new SongResponse(
                song.getId(),
                song.getTitle(),
                song.getDurationSeconds(),
                song.getTrackNumber(),
                song.getPlayCount(),
                song.getLikeCount(),
                song.getReleaseDate(),
                song.isExplicit(),
                cover,
                song.getAudioUrl() != null && !song.getAudioUrl().isBlank(),
                artist,
                album,
                genres,
                liked);
    }

    public GenreResponse toGenreResponse(Genre genre) {
        if (genre == null) {
            return null;
        }
        return new GenreResponse(
                genre.getId(),
                genre.getName(),
                genre.getSlug(),
                artworkUrlResolver.resolve(genre.getCoverImageUrl()));
    }

    public List<GenreResponse> toGenreResponses(Set<Genre> genres) {
        return genres == null ? List.of() : genres.stream().map(this::toGenreResponse).toList();
    }
}
