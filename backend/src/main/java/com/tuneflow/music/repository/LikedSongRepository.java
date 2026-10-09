package com.tuneflow.music.repository;

import com.tuneflow.music.entity.LikedSong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LikedSongRepository extends JpaRepository<LikedSong, LikedSong.LikedSongId> {

    boolean existsById_UserIdAndId_SongId(UUID userId, UUID songId);

    Page<LikedSong> findById_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("select ls.id.songId from LikedSong ls where ls.id.userId = :userId and ls.id.songId in :songIds")
    List<UUID> findLikedSongIds(@Param("userId") UUID userId, @Param("songIds") List<UUID> songIds);

    @Modifying
    @Query("delete from LikedSong ls where ls.id.userId = :userId and ls.id.songId = :songId")
    int deleteLike(@Param("userId") UUID userId, @Param("songId") UUID songId);

    /** Seeds the recommendation engine with the user's taste profile. */
    @Query("select ls.id.songId from LikedSong ls where ls.id.userId = :userId order by ls.createdAt desc")
    List<UUID> findRecentLikedSongIds(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Collaborative filtering signal (docs section 25, version 2): songs liked by other users who
     * share at least one like with this user, ranked by how many of those users liked them and
     * excluding anything the user already has.
     */
    @Query("""
            select ls2.id.songId as songId, count(ls2.id.userId) as shared
            from LikedSong ls1, LikedSong ls2
            where ls1.id.userId = :userId
              and ls2.id.userId <> :userId
              and ls2.id.songId = ls1.id.songId
              and ls2.id.songId not in (select ls3.id.songId from LikedSong ls3 where ls3.id.userId = :userId)
            group by ls2.id.songId
            order by shared desc
            """)
    List<Object[]> findCollaborativeSongIds(@Param("userId") UUID userId, Pageable pageable);

    long countById_UserId(UUID userId);
}
