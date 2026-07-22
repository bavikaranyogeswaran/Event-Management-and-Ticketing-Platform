package com.ticketing.file;

/** The essential facts the provider reports about a stored object, checked when an upload completes. */
public record StoredObject(String mime, long sizeBytes) {
}
