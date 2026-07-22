package com.ticketing.file.dto;

import java.util.UUID;

import com.ticketing.file.SignedUpload;

/** The metadata id to complete against, plus everything the browser posts to the provider. */
public record UploadRequestResponse(
        UUID fileId,
        String uploadUrl,
        String apiKey,
        long timestamp,
        String publicId,
        String folder,
        String signature) {

    public static UploadRequestResponse of(UUID fileId, SignedUpload upload) {
        return new UploadRequestResponse(fileId, upload.uploadUrl(), upload.apiKey(),
                upload.timestamp(), upload.publicId(), upload.folder(), upload.signature());
    }
}
