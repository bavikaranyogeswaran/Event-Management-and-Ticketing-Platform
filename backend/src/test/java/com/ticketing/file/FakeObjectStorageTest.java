package com.ticketing.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the fake's behaviour, since later file tests rely on it standing in for Cloudinary. */
class FakeObjectStorageTest {

    private final FakeObjectStorage storage = new FakeObjectStorage();

    @Test
    void signingReturnsTheChosenIdAndFolderWithParams() {
        SignedUpload signed = storage.signUpload("banners/abc", "banners");

        assertThat(signed.publicId()).isEqualTo("banners/abc");
        assertThat(signed.folder()).isEqualTo("banners");
        assertThat(signed.signature()).isNotBlank();
        assertThat(signed.uploadUrl()).isNotBlank();
        assertThat(signed.apiKey()).isNotBlank();
    }

    @Test
    void anObjectIsInvisibleUntilUploadedThenFindable() {
        assertThat(storage.find("x")).isEmpty();

        storage.simulateUpload("x", "image/png", 1234);

        assertThat(storage.find("x")).get()
                .extracting(StoredObject::mime, StoredObject::sizeBytes)
                .containsExactly("image/png", 1234L);
    }

    @Test
    void destroyRemovesTheObjectAndRecordsTheCall() {
        storage.simulateUpload("y", "image/jpeg", 10);

        storage.destroy("y");

        assertThat(storage.find("y")).isEmpty();
        assertThat(storage.wasDestroyed("y")).isTrue();
    }

    @Test
    void destroyingAMissingObjectDoesNotThrow() {
        storage.destroy("ghost");

        assertThat(storage.exists("ghost")).isFalse();
    }

    @Test
    void imageUrlIsDerivedFromThePublicId() {
        assertThat(storage.imageUrl("banners/abc")).contains("banners/abc");
    }
}
