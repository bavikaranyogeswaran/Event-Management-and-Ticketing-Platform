package com.ticketing.file;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory ObjectStorage for tests: no network, with hooks to simulate an upload and inspect destroys. */
public class FakeObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new HashMap<>();
    private final Set<String> destroyed = new HashSet<>();

    @Override
    public SignedUpload signUpload(String publicId, String folder) {
        return new SignedUpload("https://fake.local/upload", "fake-key",
                1_700_000_000L, publicId, "fake-signature-" + publicId);
    }

    @Override
    public Optional<StoredObject> find(String publicId) {
        return Optional.ofNullable(objects.get(publicId));
    }

    @Override
    public void destroy(String publicId) {
        objects.remove(publicId);
        destroyed.add(publicId);
    }

    @Override
    public String imageUrl(String publicId) {
        return "https://fake.cdn/" + publicId;
    }

    // ---- test hooks ----

    /** Stands in for the browser having uploaded a file, so a later find() sees it. */
    public void simulateUpload(String publicId, String mime, long sizeBytes) {
        objects.put(publicId, new StoredObject(mime, sizeBytes));
    }

    public boolean wasDestroyed(String publicId) {
        return destroyed.contains(publicId);
    }

    public boolean exists(String publicId) {
        return objects.containsKey(publicId);
    }

    public void reset() {
        objects.clear();
        destroyed.clear();
    }
}
