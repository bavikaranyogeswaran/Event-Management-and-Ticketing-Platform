package com.ticketing.file;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobService;
import com.ticketing.shared.api.ResourceNotFoundException;

/** Marks a file asset deleted and schedules its Cloudinary copy for async destruction. */
@Service
public class FileDeleteService {

    private final FileAssetRepository files;
    private final OutboxJobService outbox;
    private final Clock clock;

    FileDeleteService(FileAssetRepository files, OutboxJobService outbox, Clock clock) {
        this.files = files;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Idempotent: if the asset is already DELETED, returns without re-enqueuing. */
    @Transactional
    public void delete(UUID userId, UUID fileId) {
        FileAsset asset = files.findByIdAndOwnerUserId(fileId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (asset.getStatus() == FileStatus.DELETED) {
            return;
        }
        asset.markDeleted(Instant.now(clock));
        outbox.enqueue(JobTypes.FILE_DELETE, JobTypes.fileDeleteKey(fileId),
                new FileDeletePayload(asset.getPublicId()));
    }

    /**
     * System-initiated delete: no owner check; marks the asset DELETED and enqueues the destroy job.
     * Safe to call if the asset is already DELETED or no longer exists.
     */
    @Transactional
    void deleteOrphan(UUID fileId) {
        FileAsset asset = files.findById(fileId).orElse(null);
        if (asset == null || asset.getStatus() == FileStatus.DELETED) {
            return;
        }
        asset.markDeleted(Instant.now(clock));
        outbox.enqueue(JobTypes.FILE_DELETE, JobTypes.fileDeleteKey(fileId),
                new FileDeletePayload(asset.getPublicId()));
    }

    // matches FileDeleteDispatcher's local FileDeletePayload on JSON field names only
    private record FileDeletePayload(String publicId) {
    }
}
