package com.ticketing.shared.pagination;

import java.util.List;
import java.util.function.Function;

/** Standard list envelope: { items, page: { limit, nextCursor, hasMore } }. */
public record PageResponse<T>(List<T> items, PageInfo page) {

    public record PageInfo(int limit, String nextCursor, boolean hasMore) {
    }

    /**
     * Builds the envelope from rows fetched with limit+1: the extra row signals more pages.
     * {@code cursorOf} produces the cursor string from the last returned item.
     */
    public static <T> PageResponse<T> of(List<T> rows, int limit, Function<T, String> cursorOf) {
        boolean hasMore = rows.size() > limit;
        List<T> items = hasMore ? List.copyOf(rows.subList(0, limit)) : rows;
        String nextCursor = hasMore ? cursorOf.apply(items.get(items.size() - 1)) : null;
        return new PageResponse<>(items, new PageInfo(limit, nextCursor, hasMore));
    }

    /** Maps items to a response type while preserving the page info. */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page);
    }
}
