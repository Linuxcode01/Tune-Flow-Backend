package com.tuneflow.album.repository;

import com.tuneflow.album.entity.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlbumRepository extends JpaRepository<Album, UUID> {

    Page<Album> findByArtist_Id(UUID artistId, Pageable pageable);

    boolean existsByArtist_IdAndTitleIgnoreCase(UUID artistId, String title);

    @Query("select a from Album a where lower(a.title) like lower(concat('%', :q, '%'))")
    Page<Album> search(@Param("q") String query, Pageable pageable);

    /** "New releases" home-feed section. */
    @Query("select a from Album a where a.releaseDate is not null order by a.releaseDate desc")
    Page<Album> findNewReleases(Pageable pageable);

    Page<Album> findByReleaseDateIsNotNullOrderByReleaseDateDesc(Pageable pageable);

    @Query("select a from Album a left join fetch a.artist where a.id = :id")
    Optional<Album> findByIdWithArtist(@Param("id") UUID id);

    @Modifying
    @Query("update Album a set a.songCount = (select count(s) from Song s where s.album.id = a.id) where a.id = :id")
    void refreshSongCount(@Param("id") UUID id);
}
