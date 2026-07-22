package com.ticketing.file;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Gives tests the in-memory ObjectStorage in place of the credential-gated Cloudinary adapter. */
@TestConfiguration
public class TestObjectStorageConfig {

    @Bean
    FakeObjectStorage objectStorage() {
        return new FakeObjectStorage();
    }
}
