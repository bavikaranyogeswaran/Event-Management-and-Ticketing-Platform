package com.ticketing.shared.pagination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.pagination.KeysetCursor.Position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void cursorRoundTrips() {
        Instant now = Instant.parse("2026-08-20T18:00:00Z");
        UUID id = UUID.randomUUID();

        Position decoded = KeysetCursor.decode(KeysetCursor.encode(now, id));

        assertThat(decoded.timestamp()).isEqualTo(now);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void blankCursorMeansFirstPage() {
        assertThat(KeysetCursor.decode(null)).isNull();
        assertThat(KeysetCursor.decode("")).isNull();
    }

    @Test
    void malformedCursorIsRejected() {
        assertThatThrownBy(() -> KeysetCursor.decode("not-a-real-cursor"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_CURSOR"));
    }

    @Test
    void clampLimitAppliesDefaultsAndCeiling() {
        assertThat(Paging.clampLimit(null, 20, 50)).isEqualTo(20);
        assertThat(Paging.clampLimit(0, 20, 50)).isEqualTo(20);
        assertThat(Paging.clampLimit(10, 20, 50)).isEqualTo(10);
        assertThat(Paging.clampLimit(999, 20, 50)).isEqualTo(50);
    }

    @Test
    void pageWithMoreRowsTrimsAndSetsCursor() {
        // fetched 3 rows for a page size of 2 -> there is a next page
        List<String> rows = List.of("a", "b", "c");
        PageResponse<String> page = PageResponse.of(rows, 2, s -> "cursor-" + s);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.page().hasMore()).isTrue();
        assertThat(page.page().nextCursor()).isEqualTo("cursor-b");
        assertThat(page.page().limit()).isEqualTo(2);
    }

    @Test
    void lastPageHasNoCursor() {
        List<String> rows = List.of("a", "b");
        PageResponse<String> page = PageResponse.of(rows, 2, s -> "cursor-" + s);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.page().hasMore()).isFalse();
        assertThat(page.page().nextCursor()).isNull();
    }
}
