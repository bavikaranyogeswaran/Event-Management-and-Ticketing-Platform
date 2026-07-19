package com.ticketing.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on background timers. Left off under the test profile so a sweep can never fire
 * in the middle of a test; the jobs themselves stay callable directly.
 */
@Configuration
@EnableScheduling
@Profile("!test")
class SchedulingConfig {
}
