package com.tuneflow.history.repository;

import com.tuneflow.history.entity.ListeningHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ListeningHistoryRepository extends JpaRepository<ListeningHistory, UUID> {

    @Query("""
            select h from ListeningHistory h
            join fetch h.song s
            left join fetch s.artist
            left join fetch s.album
            where h.user.id = :userId
            order by h.startedAt desc
            """)
    Page<ListeningHistory> findByUserWithSong(@Param("userId") UUID userId, Pageable pageable);

    /** The most recent N rows are de-duplicated in the service into "recently played". */
    @Query("""
            select h from ListeningHistory h
            join fetch h.song s
            left join fetch s.artist
            left join fetch s.album
            where h.user.id = :userId
            order by h.startedAt desc
            """)
    List<ListeningHistory> findRecent(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select h.song.id, count(h) as plays from ListeningHistory h
            where h.user.id = :userId
            group by h.song.id
            order by plays desc
            """)
    List<Object[]> findMostPlayedSongIds(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select distinct s.artist.id from ListeningHistory h
            join h.song s
            where h.user.id = :userId
            """)
    List<UUID> findListenedArtistIds(@Param("userId") UUID userId);

    @Query("select coalesce(sum(h.playedSeconds), 0) from ListeningHistory h where h.user.id = :userId")
    long sumPlayedSeconds(@Param("userId") UUID userId);

    long countByUser_Id(UUID userId);

    long countBySong_Id(UUID songId);

    @Query("select count(distinct h.song.id) from ListeningHistory h where h.user.id = :userId")
    long countDistinctSongs(@Param("userId") UUID userId);

    @Query("""
            select count(h) from ListeningHistory h
            where h.user.id = :userId and h.completedAt is not null
            """)
    long countCompletedPlays(@Param("userId") UUID userId);

    /** Most-played artists, aggregated over the user's history. Returns (id, name, imageUrl, plays). */
    @Query("""
            select a.id, a.name, a.profileImageUrl, count(h) as plays
            from ListeningHistory h
            join h.song s
            join s.artist a
            where h.user.id = :userId
            group by a.id, a.name, a.profileImageUrl
            order by plays desc
            """)
    List<Object[]> findTopArtists(@Param("userId") UUID userId, Pageable pageable);

    /** Most-played genres, aggregated over the user's history. Returns (id, name, slug, plays). */
    @Query("""
            select g.id, g.name, g.slug, count(h) as plays
            from ListeningHistory h
            join h.song s
            join s.genres g
            where h.user.id = :userId
            group by g.id, g.name, g.slug
            order by plays desc
            """)
    List<Object[]> findTopGenres(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Raw start timestamps inside a window; bucketed per day in the service so the query stays
     * portable between PostgreSQL and H2 (no database-specific date functions).
     */
    @Query("""
            select h.startedAt from ListeningHistory h
            where h.user.id = :userId and h.startedAt >= :since
            """)
    List<Instant> findStartedAtSince(@Param("userId") UUID userId, @Param("since") Instant since);

    /** Housekeeping: history older than the retention window can be archived/pruned. */
    @Modifying
    @Query("delete from ListeningHistory h where h.startedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
