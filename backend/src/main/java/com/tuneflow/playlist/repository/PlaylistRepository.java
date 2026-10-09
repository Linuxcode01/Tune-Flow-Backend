package com.tuneflow.playlist.repository;

import com.tuneflow.playlist.entity.Playlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    Page<Playlist> findByOwner_Id(UUID ownerId, Pageable pageable);

    List<Playlist> findByOwner_IdAndSystemPlaylistTrue(UUID ownerId);

    Optional<Playlist> findByOwner_IdAndNameIgnoreCaseAndSystemPlaylistTrue(UUID ownerId, String name);

    @Query("""
            select p from Playlist p
            where p.publicVisible = true and lower(p.name) like lower(concat('%', :q, '%'))
            """)
    Page<Playlist> searchPublic(@Param("q") String query, Pageable pageable);

    @Query("select p from Playlist p where p.publicVisible = true order by p.createdAt desc")
    Page<Playlist> findPublicPlaylists(Pageable pageable);

    long countByOwner_Id(UUID ownerId);
}
