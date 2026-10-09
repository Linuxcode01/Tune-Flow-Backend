package com.tuneflow.streaming.service;

import com.tuneflow.common.response.PageResponse;
import com.tuneflow.streaming.dto.CreateDownloadRequest;
import com.tuneflow.streaming.dto.DownloadResponse;
import com.tuneflow.streaming.dto.StreamUrlResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StreamingService {

    /** Authorizes playback and returns a short-lived signed URL (docs sections 17, 40). */
    StreamUrlResponse createStreamUrl(UUID songId);

    DownloadResponse startDownload(CreateDownloadRequest request);

    DownloadResponse completeDownload(UUID downloadId);

    PageResponse<DownloadResponse> getDownloads(Pageable pageable);

    void revokeDownload(UUID downloadId);
}
