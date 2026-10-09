package com.tuneflow;

import com.tuneflow.album.entity.Album;
import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.GenreRepository;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.user.entity.User;
import com.tuneflow.user.repository.UserRepository;
import com.tuneflow.common.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL integration test (docs section 31: "Integration - Spring Boot Test +
 * Testcontainers for PostgreSQL").
 *
 * <p>Verifies that the PostgreSQL migration set applies cleanly, that Hibernate's schema validation
 * passes against the real database, and that the catalog queries the app relies on (search,
 * trending, genre filter, counters) behave as expected.
 *
 * <p>{@code disabledWithoutDocker = true} keeps {@code mvn test} green on machines without Docker;
 * run it explicitly with {@code mvn verify -Ddocker.tests=true} on a Docker-enabled host or in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tuneflow")
            .withUsername("tuneflow")
            .withPassword("tuneflow");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The PostgreSQL migration set - the same files production runs.
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
        registry.add("tuneflow.seed.enabled", () -> "false");
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private GenreRepository genreRepository;

    @Test
    @DisplayName("PostgreSQL migrations apply and catalog queries behave on the real database")
    void catalogQueriesWorkOnPostgres() {
        Genre synthwave = genreRepository.save(Genre.builder().name("Synthwave").slug("synthwave").build());
        Genre rock = genreRepository.save(Genre.builder().name("Rock").slug("rock").build());

        User user = userRepository.save(User.builder()
                .name("Aria Vance")
                .email("aria@tuneflow.dev")
                .passwordHash("$2a$10$notarealhash")
                .role(Role.ARTIST)
                .build());

        Artist artist = artistRepository.save(Artist.builder()
                .user(user).name("Aria Vance").country("India").verified(true).build());

        Album album = albumRepository.save(Album.builder()
                .artist(artist).title("Midnight Circuits").releaseDate(LocalDate.now().minusDays(3)).build());

        Song song = songRepository.save(Song.builder()
                .artist(artist).album(album).title("Neon Skyline")
                .durationSeconds(214).trackNumber(1).releaseDate(album.getReleaseDate())
                .audioUrl("audio/2026/10/neon.mp3")
                .genres(new LinkedHashSet<>(List.of(synthwave, rock)))
                .build());

        albumRepository.refreshSongCount(album.getId());

        assertThat(songRepository.findByIdWithDetails(song.getId()))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getTitle()).isEqualTo("Neon Skyline");
                    assertThat(found.getArtist().getName()).isEqualTo("Aria Vance");
                });

        assertThat(songRepository.search("neon", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(songRepository.findByArtist_IdAndActiveTrue(artist.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
        assertThat(songRepository.findPopularByGenreSlugs(List.of("synthwave"), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);

        songRepository.incrementPlayCount(song.getId());
        songRepository.incrementLikeCount(song.getId());
        assertThat(songRepository.findById(song.getId()))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getPlayCount()).isEqualTo(1);
                    assertThat(reloaded.getLikeCount()).isEqualTo(1);
                });

        assertThat(albumRepository.findById(album.getId()))
                .get()
                .satisfies(reloaded -> assertThat(reloaded.getSongCount()).isEqualTo(1));
    }
}
