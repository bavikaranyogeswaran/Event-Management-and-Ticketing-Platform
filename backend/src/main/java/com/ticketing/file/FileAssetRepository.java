package com.ticketing.file;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {

    Optional<FileAsset> findByPublicId(String publicId);

    // owner-scoped fetch; a non-owner gets an empty result, never someone else's asset
    Optional<FileAsset> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
