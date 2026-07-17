package com.ticketing.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All tunable application settings; values come from application.yml (env-overridable). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Api api,
        String baseUrl, // frontend origin used to build links in emails
        Auth auth,
        Email email) {

    public record Api(String basePath) {
    }

    public record Auth(
            Duration emailVerificationTtl,
            Duration passwordResetTtl) {
    }

    public record Email(boolean logLinks) { // dev aid: print email links to the log until real sending exists
    }
}
