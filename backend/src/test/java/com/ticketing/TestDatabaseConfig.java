package com.ticketing;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Starts each test run from an empty ticketing_test. Without this the schema only ever migrates,
 * so rows written by non-transactional tests would pile up from one run to the next.
 */
@Configuration
@Profile("test")
class TestDatabaseConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
