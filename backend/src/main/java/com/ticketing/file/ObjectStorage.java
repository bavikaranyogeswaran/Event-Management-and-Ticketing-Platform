package com.ticketing.file;

import java.time.Duration;
import java.util.Optional;

/** How the app stores and serves uploaded files, kept behind a port so the provider stays swappable. */
public interface ObjectStorage {

    /** The parameters a browser needs to upload a file directly to the provider under the given id. */
    SignedUpload signUpload(String publicId, String folder);

    /** What the provider reports for a stored object, used to confirm an upload really landed. Empty if none. */
    Optional<StoredObject> find(String publicId);

    /** Removes the object; a no-op if it is already gone. */
    void destroy(String publicId);

    /** A public CDN URL for an image, delivered in an auto-chosen format and quality. */
    String imageUrl(String publicId);

    /** A time-limited signed URL for a private file (e.g. CSV export). Expires after {@code ttl}. */
    String signedDownloadUrl(String publicId, Duration ttl);

    /** Uploads raw bytes to the provider as a private file and returns what was stored. */
    StoredObject uploadRaw(String publicId, byte[] bytes, String mime);
}
