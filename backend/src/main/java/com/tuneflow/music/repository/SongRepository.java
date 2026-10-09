package com.tuneflow.music.repository;

import com.tuneflow.music.entity.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SongRepository extends JpaRepository<Song, UUID> {

    Page<Song> findByActiveTrue(Pageable pageable);

    Page<Song> findByArtist_IdAndActiveTrue(UUID artistId, Pageable pageable);

    Page<Song> findByAlbum_IdAndActiveTrue(UUID albumId, Pageable pageable);

    @Query("select s from Song s left join fetch s.album left join fetch s.artist where s.id = :id")
    Optional<Song> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            select s from Song s
            where s.active = true
              and (lower(s.title) like lower(concat('%', :q, '%'))
                   or lower(s.artist.name) like lower(concat('%', :q, '%'))
                   or lower(s.album.title) like lower(concat('%', :q, '%')))
            """)
    Page<Song> search(@Param("q") String query, Pageable pageable);

    /** Trending: highest play counts first. Backed by idx_songs_play_count. */
    @Query("select s from Song s where s.active = true order by s.playCount desc, s.createdAt desc")
    Page<Song> findTrending(Pageable pageable);

    /** New releases: newest release dates first. */
    @Query("""
            select s from Song s
            where s.active = true and s.releaseDate is not null
            order by s.releaseDate desc, s.createdAt desc
            """)
    Page<Song> findNewReleases(Pageable pageable);

    /** Rule-based recommendation building block: popular songs within a set of genres. */
    @Query("""
            select distinct s from Song s
            join s.genres g
            where s.active = true and g.slug in :genreSlugs
            order by s.playCount desc
            """)
    Page<Song> findPopularByGenreSlugs(@Param("genreSlugs") Collection<String> genreSlugs, Pageable pageable);

    /** Similar content for the "because you listened to X" rail. */
    @Query("""
            select distinct s from Song s
            join s.genres g
            where s.active = true and g.id in :genreIds and s.id <> :excludeSongId
            order by s.playCount desc
            """)
    Page<Song> findSimilarByGenreIds(@Param("genreIds") Collection<UUID> genreIds,
                                     @Param("excludeSongId") UUID excludeSongId,
                                     Pageable pageable);

    @Query("select s from Song s where s.id in :ids")
    List<Song> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true)
    @Query("update Song s set s.playCount = s.playCount + 1 where s.id = :id")
    int incrementPlayCount(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update Song s set s.likeCount = s.likeCount + 1 where s.id = :id")
    int incrementLikeCount(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update Song s set s.likeCount = case when s.likeCount > 0 then s.likeCount - 1 else 0 end where s.id = :id")
    int decrementLikeCount(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update Song s set s.downloadCount = s.downloadCount + 1 where s.id = :id")
    int incrementDownloadCount(@Param("id") UUID id);

    long countByArtist_Id(UUID artistId);

    long countByActiveTrue();
}
