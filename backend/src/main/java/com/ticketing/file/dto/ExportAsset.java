package com.ticketing.file.dto;

import java.util.UUID;

/** The ids the caller needs after reserving a slot for a system-generated export file. */
public record ExportAsset(UUID fileId, String publicId) {
}
