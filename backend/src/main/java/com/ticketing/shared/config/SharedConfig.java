package com.ticketing.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans shared by all modules. */
@Configuration
class SharedConfig {

    /** Injectable clock so services never call Instant.now() directly — tests can freeze time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
