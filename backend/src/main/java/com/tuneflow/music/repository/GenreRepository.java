package com.tuneflow.music.repository;

import com.tuneflow.music.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenreRepository extends JpaRepository<Genre, UUID> {

    Optional<Genre> findBySlug(String slug);

    Optional<Genre> findByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    List<Genre> findBySlugIn(Collection<String> slugs);
}
