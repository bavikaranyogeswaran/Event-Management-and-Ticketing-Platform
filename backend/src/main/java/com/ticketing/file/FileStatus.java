package com.ticketing.file;

public enum FileStatus {
    /** Metadata created; the browser may or may not have uploaded the bytes yet. */
    PENDING,
    /** Upload verified against the provider and attached. */
    READY,
    /** Detached; kept only until the provider copy is destroyed. */
    DELETED
}
