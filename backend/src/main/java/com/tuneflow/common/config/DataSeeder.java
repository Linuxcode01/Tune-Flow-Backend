package com.tuneflow.common.config;

import com.tuneflow.album.entity.Album;
import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.entity.FollowedArtist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.artist.repository.FollowedArtistRepository;
import com.tuneflow.common.security.Role;
import com.tuneflow.history.entity.ListeningHistory;
import com.tuneflow.history.repository.ListeningHistoryRepository;
import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.LikedSong;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.GenreRepository;
import com.tuneflow.music.repository.LikedSongRepository;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.playlist.entity.Playlist;
import com.tuneflow.playlist.entity.PlaylistSong;
import com.tuneflow.playlist.repository.PlaylistRepository;
import com.tuneflow.playlist.repository.PlaylistSongRepository;
import com.tuneflow.user.entity.User;
import com.tuneflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds genres, demo accounts and a small catalog so the API, Swagger UI and the Flutter app have
 * something to show immediately after a fresh clone.
 *
 * <p>Enabled only with {@code tuneflow.seed.enabled=true} (the {@code dev} profile). Production
 * runs with seeding disabled; the seeder also refuses to run when the catalog is not empty.
 *
 * <p><b>Content licensing:</b> the demo tracks point at publicly available sample MP3s purely so
 * playback can be exercised end to end. Replace them with content you hold rights to before any
 * real deployment (docs section 39).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tuneflow.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "Password123";

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final GenreRepository genreRepository;
    private final LikedSongRepository likedSongRepository;
    private final FollowedArtistRepository followedArtistRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final ListeningHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (songRepository.count() > 0) {
            log.info("Catalog already populated; skipping demo seeding");
            return;
        }
        log.info("Seeding TuneFlow demo data (dev profile)");

        Set<Genre> genres = seedGenres();
        User admin = seedUser("TuneFlow Admin", "admin@tuneflow.dev", Role.ADMIN);
        User artistUser = seedUser("Aria Vance", "artist@tuneflow.dev", Role.ARTIST);
        User listener = seedUser("Chandan", "listener@tuneflow.dev", Role.USER);

        Artist artist = artistRepository.findByUser_Id(artistUser.getId())
                .orElseGet(() -> artistRepository.save(Artist.builder()
                        .user(artistUser)
                        .name("Aria Vance")
                        .bio("Synthwave producer from Bengaluru. Building warm, wide soundscapes.")
                        .country("India")
                        .verified(true)
                        .build()));

        Album midnight = albumRepository.save(Album.builder()
                .artist(artist)
                .title("Midnight Circuits")
                .description("Eight tracks of neon-lit synthwave.")
                .releaseDate(LocalDate.now().minusDays(21))
                .build());

        Album aurora = albumRepository.save(Album.builder()
                .artist(artist)
                .title("Aurora Sketches")
                .description("A quieter, ambient companion record.")
                .releaseDate(LocalDate.now().minusMonths(7))
                .build());

        List<Song> songs = new ArrayList<>();
        songs.add(song(midnight, artist, "Neon Skyline", 214, 1, 1, Set.of("electronic", "synthwave")));
        songs.add(song(midnight, artist, "Midnight Circuits", 241, 2, 2, Set.of("electronic", "synthwave")));
        songs.add(song(midnight, artist, "Analog Heart", 198, 3, 3, Set.of("electronic", "indie")));
        songs.add(song(midnight, artist, "Static Bloom", 226, 4, 4, Set.of("electronic")));
        songs.add(song(aurora, artist, "Aurora Sketches", 302, 1, 5, Set.of("ambient", "classical")));
        songs.add(song(aurora, artist, "Slow Light", 268, 2, 6, Set.of("ambient", "lo-fi")));

        // Give the recording some history so trending/popular rails are not empty on first run.
        Song first = songs.get(0);
        first.setPlayCount(1240);
        first.setLikeCount(1);
        songs.get(1).setPlayCount(860);
        songs.get(2).setPlayCount(540);
        songRepository.saveAll(songs);

        albumRepository.refreshSongCount(midnight.getId());
        albumRepository.refreshSongCount(aurora.getId());

        seedLibrary(listener, artist, songs);

        log.info("""
                Seeded demo data.
                  Accounts (password: {}): {}, {}, {}""",
                DEMO_PASSWORD,
                "admin@tuneflow.dev (ADMIN)",
                "artist@tuneflow.dev (ARTIST)",
                "listener@tuneflow.dev (USER)");
        // Referenced so the compiler keeps the admin variable meaningful for future admin seeding.
        log.debug("Admin account id: {}", admin.getId());
    }

    // ---------------------------------------------------------------------------------------------

    private Set<Genre> seedGenres() {
        record GenreSeed(String name, String slug) {
        }
        List<GenreSeed> seeds = List.of(
                new GenreSeed("Rock", "rock"),
                new GenreSeed("Pop", "pop"),
                new GenreSeed("Hip-Hop", "hip-hop"),
                new GenreSeed("Electronic", "electronic"),
                new GenreSeed("Synthwave", "synthwave"),
                new GenreSeed("Indie", "indie"),
                new GenreSeed("Jazz", "jazz"),
                new GenreSeed("Classical", "classical"),
                new GenreSeed("Ambient", "ambient"),
                new GenreSeed("Lo-Fi", "lo-fi"));

        Set<Genre> created = new LinkedHashSet<>();
        for (GenreSeed seed : seeds) {
            created.add(genreRepository.findBySlug(seed.slug())
                    .orElseGet(() -> genreRepository.save(Genre.builder()
                            .name(seed.name())
                            .slug(seed.slug())
                            .build())));
        }
        return created;
    }

    private User seedUser(String name, String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(User.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role)
                .enabled(true)
                .build()));
    }

    private Song song(Album album, Artist artist, String title, int duration, int trackNumber,
                      int demoIndex, Set<String> genreSlugs) {
        return Song.builder()
                .album(album)
                .artist(artist)
                .title(title)
                .durationSeconds(duration)
                .trackNumber(trackNumber)
                .releaseDate(album.getReleaseDate())
                .audioUrl("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-" + demoIndex + ".mp3")
                .genres(new LinkedHashSet<>(genreRepository.findBySlugIn(genreSlugs)))
                .active(true)
                .build();
    }

    private void seedLibrary(User listener, Artist artist, List<Song> songs) {
        // Likes
        for (Song song : songs.subList(0, 3)) {
            likedSongRepository.save(LikedSong.builder()
                    .id(new LikedSong.LikedSongId(listener.getId(), song.getId()))
                    .user(listener)
                    .song(song)
                    .build());
        }

        // Follow
        followedArtistRepository.save(FollowedArtist.builder()
                .id(new FollowedArtist.FollowedArtistId(listener.getId(), artist.getId()))
                .user(listener)
                .artist(artist)
                .build());
        artist.setFollowerCount(artist.getFollowerCount() + 1);
        artistRepository.save(artist);

        // Playlist
        Playlist playlist = playlistRepository.save(Playlist.builder()
                .owner(listener)
                .name("Late Night Drive")
                .description("Synthwave for long drives.")
                .publicVisible(true)
                .build());
        int position = 0;
        for (Song song : songs.subList(0, 4)) {
            playlistSongRepository.save(PlaylistSong.builder()
                    .id(new PlaylistSong.PlaylistSongId(playlist.getId(), song.getId()))
                    .playlist(playlist)
                    .song(song)
                    .position(position++)
                    .addedBy(listener.getEmail())
                    .build());
        }
        playlist.setSongCount(position);
        playlist.setTotalDurationSeconds(songs.subList(0, 4).stream()
                .mapToInt(Song::getDurationSeconds).sum());
        playlistRepository.save(playlist);

        // History so "Recently played" and recommendations have signal
        int index = 0;
        for (Song song : songs.subList(0, 5)) {
            historyRepository.save(ListeningHistory.builder()
                    .user(listener)
                    .song(song)
                    .startedAt(Instant.now().minusSeconds(3600L * (++index)))
                    .playedSeconds(song.getDurationSeconds())
                    .completionRatio(1.0)
                    .completedAt(Instant.now().minusSeconds(3600L * index - 60))
                    .deviceId("demo-device")
                    .build());
        }
    }
}
