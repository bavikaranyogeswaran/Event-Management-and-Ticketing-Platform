package com.ticketing.file;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the suite off the live Cloudinary account. The repo's .env carries real keys onto the
 * classpath during tests; only the blank override in application-test.yml stops the real adapter
 * being wired. Losing that would fail nothing loudly, hence these checks.
 */
@Import(TestObjectStorageConfig.class)
class CloudinaryObjectStorageWiringTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationContext context;
    @Autowired
    Environment environment;

    @Test
    void theOnlyStorageAvailableToTestsIsTheStandIn() {
        assertThat(context.getBeansOfType(ObjectStorage.class))
                .isNotEmpty()
                .allSatisfy((name, storage) -> assertThat(storage).isInstanceOf(FakeObjectStorage.class));
    }

    @Test
    void theRealAdapterIsNeverConstructed() {
        assertThat(context.getBeansOfType(CloudinaryObjectStorage.class)).isEmpty();
    }

    @Test
    void theApiKeyStaysBlankUnderTheTestProfile() {
        assertThat(environment.getProperty("app.cloudinary.api-key", "")).isEmpty();
    }
}
