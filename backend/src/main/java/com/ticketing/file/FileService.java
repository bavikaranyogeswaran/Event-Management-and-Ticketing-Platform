package com.ticketing.file;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.file.dto.DownloadUrlResponse;
import com.ticketing.file.dto.ExportAsset;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.port.IdGenerator;

/** The file module's boundary for other modules: confirming a banner may be attached, and building view URLs. */
@Service
public class FileService {

    private final FileAssetRepository files;
    private final Optional<ObjectStorage> storage;
    private final AuditService auditService;
    private final FileProperties properties;
    private final IdGenerator idGenerator;

    FileService(FileAssetRepository files, Optional<ObjectStorage> storage,
            AuditService auditService, FileProperties properties, IdGenerator idGenerator) {
        this.files = files;
        this.storage = storage;
        this.auditService = auditService;
        this.properties = properties;
        this.idGenerator = idGenerator;
    }

    /** Confirms the file is a ready banner the user uploaded; throws otherwise. */
    @Transactional(readOnly = true)
    public void confirmEventBanner(UUID userId, UUID fileId) {
        confirmReadyImage(userId, fileId, FilePurpose.EVENT_BANNER);
    }

    /** Confirms the file is a ready profile image the user uploaded; throws otherwise. */
    @Transactional(readOnly = true)
    public void confirmProfileImage(UUID userId, UUID fileId) {
        confirmReadyImage(userId, fileId, FilePurpose.PROFILE_IMAGE);
    }

    private void confirmReadyImage(UUID userId, UUID fileId, FilePurpose purpose) {
        FileAsset asset = files.findByIdAndOwnerUserId(fileId, userId).orElseThrow(ResourceNotFoundException::new);
        if (asset.getPurpose() != purpose || !asset.isReady()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FileErrorCodes.INVALID_UPLOAD_REQUEST,
                    "That file is not a ready image for this.");
        }
    }

    /** The public URL for a stored image, or empty when there is nothing ready to show. */
    @Transactional(readOnly = true)
    public Optional<String> imageUrl(UUID fileId) {
        if (fileId == null) {
            return Optional.empty();
        }
        return files.findById(fileId)
                .filter(FileAsset::isReady)
                .flatMap(asset -> storage.map(provider -> provider.imageUrl(asset.getPublicId())));
    }

    /**
     * Reserves a PENDING file_asset slot for a system-generated export.
     * Must be called inside an existing transaction so the record and its job commit together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ExportAsset createExportRecord(UUID ownerUserId) {
        UUID fileId = idGenerator.newId();
        String publicId = properties.exportFolder() + "/" + fileId;
        FileAsset asset = new FileAsset(fileId, ownerUserId, null, FilePurpose.EXPORT, publicId, "text/csv", 0L);
        files.save(asset);
        return new ExportAsset(fileId, publicId);
    }

    /** Generates a time-limited signed download URL for a ready EXPORT file owned by the caller. */
    @Transactional
    public DownloadUrlResponse signedExportUrl(UUID fileId, UUID ownerUserId) {
        FileAsset asset = files.findByIdAndOwnerUserId(fileId, ownerUserId)
                .orElseThrow(ResourceNotFoundException::new);
        if (asset.getPurpose() != FilePurpose.EXPORT || !asset.isReady()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FileErrorCodes.INVALID_UPLOAD_REQUEST,
                    "That file is not a ready export.");
        }
        ObjectStorage provider = storage.orElseThrow(this::storageNotConfigured);
        Duration ttl = properties.exportDownloadTtl();
        String url = provider.signedDownloadUrl(asset.getPublicId(), ttl);
        auditService.record(AuditActions.EXPORT_DOWNLOADED, ownerUserId, "FILE_ASSET", fileId, null);
        return new DownloadUrlResponse(url, Instant.now().plus(ttl));
    }

    private ApiException storageNotConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, FileErrorCodes.STORAGE_NOT_CONFIGURED,
                "File storage is not configured.");
    }
}
