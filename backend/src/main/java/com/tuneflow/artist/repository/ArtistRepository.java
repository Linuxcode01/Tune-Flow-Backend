package com.tuneflow.artist.repository;

import com.tuneflow.artist.entity.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, UUID> {

    Optional<Artist> findByUser_Id(UUID userId);

    boolean existsByNameIgnoreCase(String name);

    Page<Artist> findByVerifiedTrue(Pageable pageable);

    @Query("select a from Artist a where lower(a.name) like lower(concat('%', :q, '%'))")
    Page<Artist> search(@Param("q") String query, Pageable pageable);

    /** "Popular artists" home-feed section. */
    @Query("select a from Artist a order by a.followerCount desc, a.name asc")
    Page<Artist> findPopular(Pageable pageable);
}
