package com.ticketing.file.dto;

import java.util.UUID;

import com.ticketing.file.FilePurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** A caller's declared intent to upload; the declared type and size are re-checked once the file lands. */
public record UploadRequest(
        @NotNull FilePurpose purpose,
        UUID eventId, // required for an event banner, ignored otherwise
        @NotBlank String mime,
        @Positive long sizeBytes) {
}
