package com.tuneflow.notification.service;

import com.tuneflow.artist.repository.FollowedArtistRepository;
import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * New-release fan-out (docs section 28).
 *
 * <p>Kept as a plain service so <em>both</em> transports can drive it: the in-process listener used
 * in development and the Kafka consumer used in production. That guarantees identical behaviour
 * whichever transport is configured - the classic source of drift between dev and prod.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewReleaseFanoutService {

    private static final String EVENT_SONG_CREATED = "song.created";

    private final FollowedArtistRepository followedArtistRepository;
    private final SongRepository songRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public void handle(DomainEvent event) {
        if (!EVENT_SONG_CREATED.equals(event.type()) || event.songId() == null) {
            return;
        }
        Optional<Song> maybeSong = songRepository.findByIdWithDetails(event.songId());
        if (maybeSong.isEmpty() || maybeSong.get().getArtist() == null) {
            return;
        }
        Song song = maybeSong.get();
        var subscribers = followedArtistRepository.findNotifySubscribers(song.getArtist().getId());

        for (var subscriber : subscribers) {
            notificationService.create(
                    subscriber.getUser().getId(),
                    Notification.NotificationType.NEW_RELEASE,
                    song.getArtist().getName() + " released a new track",
                    song.getTitle(),
                    "{\"screen\":\"player\",\"songId\":\"" + song.getId() + "\"}");
        }
        if (!subscribers.isEmpty()) {
            log.info("Queued new-release notifications for {} follower(s) of artist {}",
                    subscribers.size(), song.getArtist().getId());
        }
    }
}
