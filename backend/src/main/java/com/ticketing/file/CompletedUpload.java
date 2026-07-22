package com.ticketing.file;

/** A verified, attached asset together with its public delivery URL. */
public record CompletedUpload(FileAsset asset, String url) {
}
