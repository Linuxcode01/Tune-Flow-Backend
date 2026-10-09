package com.tuneflow.artist.repository;

import com.tuneflow.artist.entity.FollowedArtist;
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
public interface FollowedArtistRepository extends JpaRepository<FollowedArtist, FollowedArtist.FollowedArtistId> {

    boolean existsById_UserIdAndId_ArtistId(UUID userId, UUID artistId);

    Page<FollowedArtist> findById_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<FollowedArtist> findById_UserId(UUID userId);

    @Modifying
    @Query("delete from FollowedArtist f where f.id.userId = :userId and f.id.artistId = :artistId")
    int deleteFollow(@Param("userId") UUID userId, @Param("artistId") UUID artistId);

    long countById_ArtistId(UUID artistId);

    /** Followers that should receive a "new release" notification. */
    @Query("select f from FollowedArtist f where f.id.artistId = :artistId and f.notifyNewReleases = true")
    List<FollowedArtist> findNotifySubscribers(@Param("artistId") UUID artistId);
}
