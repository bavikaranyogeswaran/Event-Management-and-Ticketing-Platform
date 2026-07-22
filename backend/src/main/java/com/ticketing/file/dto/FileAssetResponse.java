package com.ticketing.file.dto;

import java.util.UUID;

import com.ticketing.file.FileAsset;
import com.ticketing.file.FilePurpose;
import com.ticketing.file.FileStatus;

/** A file asset and where to view it. */
public record FileAssetResponse(UUID id, FilePurpose purpose, FileStatus status, String url) {

    public static FileAssetResponse of(FileAsset asset, String url) {
        return new FileAssetResponse(asset.getId(), asset.getPurpose(), asset.getStatus(), url);
    }
}
