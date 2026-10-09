package com.tuneflow.playlist.repository;

import com.tuneflow.playlist.entity.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, PlaylistSong.PlaylistSongId> {

    @Query("""
            select ps from PlaylistSong ps
            join fetch ps.song s
            left join fetch s.artist
            left join fetch s.album
            where ps.id.playlistId = :playlistId
            order by ps.position asc
            """)
    List<PlaylistSong> findItemsWithSongs(@Param("playlistId") UUID playlistId);

    Optional<PlaylistSong> findById_PlaylistIdAndId_SongId(UUID playlistId, UUID songId);

    @Query("select coalesce(max(ps.position), -1) from PlaylistSong ps where ps.id.playlistId = :playlistId")
    int findMaxPosition(@Param("playlistId") UUID playlistId);

    @Modifying
    @Query("delete from PlaylistSong ps where ps.id.playlistId = :playlistId and ps.id.songId = :songId")
    int deleteItem(@Param("playlistId") UUID playlistId, @Param("songId") UUID songId);

    @Modifying
    @Query("delete from PlaylistSong ps where ps.id.playlistId = :playlistId")
    int deleteAllItems(@Param("playlistId") UUID playlistId);

    int countById_PlaylistId(UUID playlistId);
}
