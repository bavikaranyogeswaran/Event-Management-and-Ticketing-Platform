package com.ticketing.shared.pagination;

import org.springframework.data.domain.Limit;

/** Small helpers for turning request params into safe paging bounds. */
public final class Paging {

    public static int clampLimit(Integer requested, int defaultLimit, int maxLimit) {
        if (requested == null || requested < 1) {
            return defaultLimit;
        }
        return Math.min(requested, maxLimit);
    }

    /** Fetch one extra row than the page size to detect whether more pages exist. */
    public static Limit fetchLimit(int pageSize) {
        return Limit.of(pageSize + 1);
    }

    private Paging() {
    }
}
