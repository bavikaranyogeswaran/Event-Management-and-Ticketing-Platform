package com.ticketing.shared.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All tunable application settings; values come from application.yml (env-overridable). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Api api,
        String baseUrl, // frontend origin used to build links in emails
        Auth auth,
        Email email,
        Order order,
        Ticket ticket,
        Cors cors) {

    public record Api(String basePath) {
    }

    public record Auth(
            Duration emailVerificationTtl,
            Duration passwordResetTtl) {
    }

    public record Email(boolean logLinks) { // dev aid: print email links to the log until real sending exists
    }

    /** Shape of the human-readable order number, e.g. ORD-2026-000042. */
    public record Order(
            String numberPrefix,
            int numberPadding,
            String numberZone) { // zone that decides which calendar year the number carries
    }

    /** Shape of the ticket code an attendee can read out loud, e.g. TCK-7F3K-9Q2M. */
    public record Ticket(
            String codePrefix,
            int codeGroups,
            int codeGroupLength) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
