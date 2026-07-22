package com.ticketing.file;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;

/** The file module's boundary for other modules: confirming a banner may be attached, and building view URLs. */
@Service
public class FileService {

    private final FileAssetRepository files;
    private final Optional<ObjectStorage> storage;

    FileService(FileAssetRepository files, Optional<ObjectStorage> storage) {
        this.files = files;
        this.storage = storage;
    }

    /** Confirms the file is a ready banner the user uploaded; throws otherwise. */
    @Transactional(readOnly = true)
    public void confirmEventBanner(UUID userId, UUID fileId) {
        FileAsset asset = files.findByIdAndOwnerUserId(fileId, userId).orElseThrow(ResourceNotFoundException::new);
        if (asset.getPurpose() != FilePurpose.EVENT_BANNER || !asset.isReady()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FileErrorCodes.INVALID_UPLOAD_REQUEST,
                    "That file is not a ready banner.");
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
}
