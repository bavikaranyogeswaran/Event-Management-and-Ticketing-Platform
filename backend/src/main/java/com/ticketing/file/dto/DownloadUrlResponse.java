package com.ticketing.file.dto;

import java.time.Instant;

public record DownloadUrlResponse(String url, Instant expiresAt) {
}
