package com.tuneflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TuneFlow - music streaming platform.
 *
 * <p>Modular Spring Boot monolith: every domain (auth, user, music, artist, album, playlist,
 * history, search, recommendation, lyrics, notification, streaming, storage, analytics) lives in
 * its own package with explicit boundaries so a high-load module can later be extracted into a
 * microservice without re-architecting the codebase.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.tuneflow")
@EnableCaching
@EnableAsync
@EnableScheduling // chunked-upload session purge; see ChunkedUploadService
public class TuneFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TuneFlowApplication.class, args);
    }
}
