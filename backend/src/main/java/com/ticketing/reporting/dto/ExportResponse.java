package com.ticketing.reporting.dto;

import java.util.UUID;

/** Returned immediately when an export job is accepted; the client polls GET /files/{fileId}/download-url. */
public record ExportResponse(UUID fileId) {
}
