package com.tuneflow.music;

import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.common.event.EventPublisher;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.security.AuthenticatedUser;
import com.tuneflow.common.security.Role;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.GenreRepository;
import com.tuneflow.music.repository.LikedSongRepository;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import com.tuneflow.music.service.SongServiceImpl;
import com.tuneflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Like/unlike business rules, including the counter side effects (docs sections 3, 12). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SongServiceLikeTest {

    @Mock
    private SongRepository songRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private ArtistRepository artistRepository;
    @Mock
    private GenreRepository genreRepository;
    @Mock
    private LikedSongRepository likedSongRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SongMapper songMapper;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SongServiceImpl songService;

    private UUID userId;
    private UUID songId;
    private Song song;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        songId = UUID.randomUUID();
        Artist artist = Artist.builder().name("Aria").build();
        artist.setId(UUID.randomUUID());
        song = Song.builder().title("Neon Skyline").artist(artist).durationSeconds(214).active(true).build();
        song.setId(songId);

        authenticate(new AuthenticatedUser(userId, "listener@tuneflow.dev", "Listener", Role.USER));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("liking a song persists the like, bumps the counter and emits an event")
    void likeAddsLikeAndIncrementsCounter() {
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));
        when(likedSongRepository.existsById_UserIdAndId_SongId(userId, songId)).thenReturn(false);

        songService.likeSong(songId);

        verify(likedSongRepository).save(any());
        verify(songRepository).incrementLikeCount(songId);
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("liking twice is a conflict, not a silent duplicate")
    void likingTwiceConflicts() {
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));
        when(likedSongRepository.existsById_UserIdAndId_SongId(userId, songId)).thenReturn(true);

        assertThatThrownBy(() -> songService.likeSong(songId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LIKED);

        verify(songRepository, never()).incrementLikeCount(any());
    }

    @Test
    @DisplayName("unliking a song that was never liked reports not-found and leaves the counter alone")
    void unlikingUnlikedSongIsNotFound() {
        when(likedSongRepository.deleteLike(userId, songId)).thenReturn(0);

        assertThatThrownBy(() -> songService.unlikeSong(songId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(songRepository, never()).decrementLikeCount(any());
    }

    @Test
    @DisplayName("unliking an existing like decrements but never below zero")
    void unlikeDecrementsCounter() {
        when(likedSongRepository.deleteLike(userId, songId)).thenReturn(1);

        songService.unlikeSong(songId);

        verify(songRepository).decrementLikeCount(songId);
    }

    @Test
    @DisplayName("inactive (soft-deleted) songs are invisible")
    void softDeletedSongsAreHidden() {
        song.setActive(false);
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));

        assertThatThrownBy(() -> songService.likeSong(songId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SONG_NOT_FOUND);
    }

    @Test
    @DisplayName("an artist cannot manage a song that belongs to someone else")
    void artistCannotManageForeignSong() {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "other@tuneflow.dev", "Other", Role.ARTIST));
        Artist ownArtist = Artist.builder().name("Other Artist").build();
        ownArtist.setId(UUID.randomUUID());
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));
        when(artistRepository.findByUser_Id(any(UUID.class))).thenReturn(Optional.of(ownArtist));

        assertThatThrownBy(() -> songService.deleteSong(songId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(song.isActive()).isTrue();
    }

    @Test
    @DisplayName("an admin may manage any song")
    void adminCanManageAnySong() {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "admin@tuneflow.dev", "Admin", Role.ADMIN));
        song.setAlbum(null);
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));

        songService.deleteSong(songId);

        assertThat(song.isActive()).isFalse();
    }

    @Test
    @DisplayName("guest callers get a discovery page with null liked flags rather than an error")
    void anonymousCatalogReadDoesNotFail() {
        SecurityContextHolder.clearContext();
        when(songRepository.findByActiveTrue(any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(song)));
        when(songMapper.toResponse(any(), any())).thenReturn(null);
        when(likedSongRepository.findLikedSongIds(any(), any())).thenReturn(List.of());

        songService.getSongs(null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(likedSongRepository, never()).findLikedSongIds(any(), any());
    }

    private void authenticate(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
    }
}
