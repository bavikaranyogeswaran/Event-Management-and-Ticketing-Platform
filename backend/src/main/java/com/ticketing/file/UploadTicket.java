package com.ticketing.file;

import java.util.UUID;

/** The result of authorising an upload: our metadata id, plus the params the browser posts to the provider. */
public record UploadTicket(UUID fileId, SignedUpload upload) {
}
