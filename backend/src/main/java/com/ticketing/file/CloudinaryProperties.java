package com.ticketing.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cloudinary account credentials; blank in tests so the real adapter is never wired there. */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {
}
