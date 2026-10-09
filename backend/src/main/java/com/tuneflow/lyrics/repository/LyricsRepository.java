package com.tuneflow.lyrics.repository;

import com.tuneflow.lyrics.entity.Lyrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LyricsRepository extends JpaRepository<Lyrics, UUID> {

    Optional<Lyrics> findBySong_IdAndLanguage(UUID songId, String language);

    List<Lyrics> findBySong_Id(UUID songId);

    boolean existsBySong_IdAndLanguage(UUID songId, String language);
}
