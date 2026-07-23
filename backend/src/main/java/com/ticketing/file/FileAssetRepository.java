package com.ticketing.file;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {

    Optional<FileAsset> findByPublicId(String publicId);

    // owner-scoped fetch; a non-owner gets an empty result, never someone else's asset
    Optional<FileAsset> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    // uses ix_file_assets_pending_cleanup; oldest first so long-standing orphans are removed before newer ones
    List<FileAsset> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            FileStatus status, Instant cutoff, Limit limit);
}
