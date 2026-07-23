package com.ticketing.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts that need no network: request signing and delivery-URL building, with dummy credentials. */
class CloudinaryObjectStorageTest {

    private final CloudinaryObjectStorage storage = new CloudinaryObjectStorage(
            new CloudinaryProperties("demo-cloud", "112233445566778", "an-api-secret"));

    @Test
    void signingProducesTheParamsTheBrowserNeeds() {
        SignedUpload signed = storage.signUpload("banners/abc123", "banners");

        assertThat(signed.publicId()).isEqualTo("banners/abc123");
        assertThat(signed.apiKey()).isEqualTo("112233445566778");
        assertThat(signed.timestamp()).isPositive();
        assertThat(signed.signature()).isNotBlank();
        assertThat(signed.uploadUrl()).contains("demo-cloud").endsWith("/image/upload");
    }

    @Test
    void aDifferentPublicIdYieldsADifferentSignature() {
        SignedUpload one = storage.signUpload("banners/one", "banners");
        SignedUpload two = storage.signUpload("banners/two", "banners");

        assertThat(one.signature()).isNotEqualTo(two.signature());
    }

    @Test
    void imageUrlIsAPublicCdnUrlWithAutoFormatAndQuality() {
        String url = storage.imageUrl("banners/abc123");

        assertThat(url).contains("demo-cloud").contains("banners/abc123")
                .contains("f_auto").contains("q_auto");
    }
}
