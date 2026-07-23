package com.ticketing.file;

/** Everything the browser posts alongside the file to upload it straight to the provider. */
public record SignedUpload(
        String uploadUrl,
        String apiKey,
        long timestamp,
        String publicId,
        String signature) {
}
