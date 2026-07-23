package com.ticketing.file;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cleans up PENDING file assets that were never completed after a configurable grace period. */
@Component
class FileOrphanSweeper {

    private static final Logger log = LoggerFactory.getLogger(FileOrphanSweeper.class);

    private final FileAssetRepository files;
    private final FileDeleteService deleteService;
    private final Clock clock;
    private final Duration orphanTtl;
    private final int batchSize;

    FileOrphanSweeper(FileAssetRepository files, FileDeleteService deleteService,
            Clock clock, FileProperties properties) {
        this.files = files;
        this.deleteService = deleteService;
        this.clock = clock;
        this.orphanTtl = properties.orphanTtl();
        this.batchSize = properties.orphanBatchSize();
    }

    @Scheduled(fixedDelayString = "${app.files.orphan-sweep-interval}")
    void scheduledSweep() {
        sweepOnce();
    }

    /** Deletes one batch of orphaned pending uploads; safe to call repeatedly. */
    int sweepOnce() {
        Instant cutoff = Instant.now(clock).minus(orphanTtl);
        List<FileAsset> orphans = files.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                FileStatus.PENDING, cutoff, Limit.of(batchSize));
        int deleted = 0;
        for (FileAsset orphan : orphans) {
            try {
                deleteService.deleteOrphan(orphan.getId());
                deleted++;
            } catch (RuntimeException e) {
                // one failure does not strand the rest of the batch
                log.warn("Could not delete orphan file {}: {}", orphan.getId(), e.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("Deleted {} orphaned file asset(s)", deleted);
        }
        if (orphans.size() == batchSize) {
            log.warn("Orphan sweep batch was full; more may wait for the next run");
        }
        return deleted;
    }
}
