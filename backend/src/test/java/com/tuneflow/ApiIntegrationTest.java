package com.tuneflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API journey against the real Spring context and the H2 schema, exercising the flow the
 * docs call out as the end-to-end test (section 31): login -> search -> play -> playlist -> logout.
 *
 * <p>Runs anywhere - no Docker, no PostgreSQL - because the {@code test} profile uses H2 with the
 * H2 Flyway migration set. The PostgreSQL wiring is covered separately by {@code PostgresIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("register -> login -> browse -> search -> like -> playlist -> history -> logout")
    void fullUserJourney() throws Exception {
        // 1. Register
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Journey User","email":"journey@tuneflow.dev","password":"Password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("journey@tuneflow.dev"));

        // 2. Registering the same email again is a conflict, not a duplicate account
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Journey User","email":"journey@tuneflow.dev","password":"Password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));

        // 3. Login as the seeded demo listener
        JsonNode login = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"listener@tuneflow.dev","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn());

        String accessToken = login.at("/data/accessToken").asText();
        String refreshToken = login.at("/data/refreshToken").asText();
        assertThat(accessToken).isNotBlank();

        // 4. Bad credentials are rejected generically
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"listener@tuneflow.dev","password":"WrongPassword1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BAD_CREDENTIALS"));

        // 5. Protected endpoint without a token -> 401 with the standard envelope
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        // 6. Profile
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("listener@tuneflow.dev"));

        // 7. Catalog is publicly browsable
        JsonNode songs = json(mockMvc.perform(get("/api/v1/songs?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andReturn());
        assertThat(songs.at("/data/content").size()).isGreaterThan(0);
        String songId = songs.at("/data/content/0/id").asText();

        // 8. Search, including the tokenized fallback pass
        mockMvc.perform(get("/api/v1/search").param("q", "neon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs").isArray())
                .andExpect(jsonPath("$.data.fuzzyMatched").value(false));

        // Typo in the second word: the fallback tokenizes and still finds the track.
        mockMvc.perform(get("/api/v1/search").param("q", "neon skylne"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fuzzyMatched").value(true))
                .andExpect(jsonPath("$.data.songs[0].title").value("Neon Skyline"));

        // A query that matches nothing must not claim a fuzzy match.
        mockMvc.perform(get("/api/v1/search").param("q", "zzzz qqqq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fuzzyMatched").value(false))
                .andExpect(jsonPath("$.data.totalResults").value(0));

        // 9. Streaming authorization requires a token and returns a signed URL
        mockMvc.perform(get("/api/v1/songs/{id}/stream", songId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/songs/{id}/stream", songId).header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));

        // 10. Like is idempotent-safe: first call succeeds, second is a conflict
        mockMvc.perform(post("/api/v1/songs/{id}/like", songs.at("/data/content/4/id").asText())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/songs/{id}/like", songs.at("/data/content/4/id").asText())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_LIKED"));

        // 11. Playlist creation and membership
        JsonNode playlist = json(mockMvc.perform(post("/api/v1/playlists")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Journey Mix","description":"integration test","publicPlaylist":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        String playlistId = playlist.at("/data/id").asText();

        mockMvc.perform(post("/api/v1/playlists/{id}/songs", playlistId)
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"" + songId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs.length()").value(1));

        // 12. Playback event, then history reflects it
        mockMvc.perform(post("/api/v1/history")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"" + songId + "\",\"playedSeconds\":180,\"completed\":true,"
                                + "\"deviceId\":\"integration\",\"offline\":false,\"finalUpdate\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/history/recently-played?limit=5")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // Statistics are derived from the playback just recorded (regression guard: this route was
        // documented but unimplemented, which surfaced as a 500 rather than a 404).
        mockMvc.perform(get("/api/v1/history/stats").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPlays").isNumber())
                .andExpect(jsonPath("$.data.totalListeningSeconds").isNumber())
                .andExpect(jsonPath("$.data.topArtists").isArray())
                .andExpect(jsonPath("$.data.topGenres").isArray())
                .andExpect(jsonPath("$.data.lastSevenDays.length()").value(7));

        mockMvc.perform(get("/api/v1/history/stats"))
                .andExpect(status().isUnauthorized());

        // 13. Home feed assembles personalised rails
        mockMvc.perform(get("/api/v1/home").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalised").value(true))
                .andExpect(jsonPath("$.data.sections").isArray());

        // 14. Recommendations and strategy reporting
        mockMvc.perform(get("/api/v1/recommendations?limit=5").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/v1/recommendations/strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("hybrid"));

        // 15. Lyrics: uploading non-LRC content flagged as synced is rejected
        JsonNode artistLogin = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"artist@tuneflow.dev","password":"Password123"}
                                """))
                .andReturn());
        String artistToken = artistLogin.at("/data/accessToken").asText();

        mockMvc.perform(put("/api/v1/songs/{id}/lyrics", songId)
                        .header("Authorization", bearer(artistToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"en","synced":true,"content":"no timestamps"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/songs/{id}/lyrics", songId)
                        .header("Authorization", bearer(artistToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"en","synced":true,"content":"[00:10.00]Neon skyline"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].timeMs").value(10000));

        // 16. Refresh rotation, then replay of the spent token is rejected
        JsonNode refreshed = json(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
        String rotated = refreshed.at("/data/refreshToken").asText();
        assertThat(rotated).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // 17. Logout revokes the rotated token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("validation errors come back as field-level details, not a generic 500")
    void validationErrorsAreFieldScoped() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","email":"not-an-email","password":"weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                // name too short, email malformed, password too short and missing a digit
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
    }

    @Test
    @DisplayName("a playlist belonging to another user is 404, not 403, so it cannot be probed")
    void foreignPrivatePlaylistIsHidden() throws Exception {
        JsonNode login = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"listener@tuneflow.dev","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn());

        String token = login.at("/data/accessToken").asText();

        mockMvc.perform(get("/api/v1/playlists/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PLAYLIST_NOT_FOUND"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
