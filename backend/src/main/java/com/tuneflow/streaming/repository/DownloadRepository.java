package com.tuneflow.streaming.repository;

import com.tuneflow.streaming.entity.Download;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DownloadRepository extends JpaRepository<Download, UUID> {

    Page<Download> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            select d from Download d
            join fetch d.song s
            left join fetch s.artist
            where d.user.id = :userId
            order by d.createdAt desc
            """)
    Page<Download> findByUserWithSong(@Param("userId") UUID userId, Pageable pageable);

    Optional<Download> findByUser_IdAndSong_IdAndDeviceId(UUID userId, UUID songId, String deviceId);

    long countByUser_IdAndStatus(UUID userId, Download.DownloadStatus status);
}
