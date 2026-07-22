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
        Payment payment,
        Cors cors,
        Messaging messaging) {

    public record Api(String basePath) {
    }

    public record Auth(
            Duration emailVerificationTtl,
            Duration passwordResetTtl) {
    }

    public record Email(
            boolean logLinks, // dev aid: reveal message bodies (which may hold tokens) in the log
            String transport, // which EmailSender is active: "log" or "smtp"
            String from) { // the From address on sent mail
    }

    /** Shape of the human-readable order number, e.g. ORD-2026-000042. */
    public record Order(
            String numberPrefix,
            int numberPadding,
            String numberZone, // zone that decides which calendar year the number carries
            Duration paymentHold, // how long an unpaid order keeps its seats
            Duration expirySweepInterval,
            int expiryBatchSize) {
    }

    /** Shape of the ticket code an attendee can read out loud, e.g. TCK-7F3K-9Q2M. */
    public record Ticket(
            String codePrefix,
            int codeGroups,
            int codeGroupLength,
            String tokenSecret, // signs QR validation tokens; must stay outside the database
            int qrPixels,
            int qrMargin) {
    }

    public record Payment(
            String secretKey,
            String webhookSecret,
            // the domain prices in LKR; this is only what the provider is asked to charge in
            String gatewayCurrency,
            String successPath,
            String cancelPath) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** RabbitMQ names for the email pipeline plus how the relay polls; the relay publishes to the exchange, the consumer reads the queue. */
    public record Messaging(
            String exchange,
            String emailQueue,
            String emailRoutingKey,
            String deadLetterExchange, // where a rejected message goes instead of looping
            String deadLetterQueue,
            Duration relayInterval, // how often the relay looks for jobs to publish
            Duration recoveryInterval, // how often it hunts for jobs stuck mid-publish
            Duration recoveryGrace, // how long a job may sit PUBLISHING before it is reclaimed
            int batchSize,
            List<Duration> retryBackoff) { // the wait before each send retry; the job dies once these run out
    }
}
