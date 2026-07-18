package com.ticketing.event;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ticketing.shared.pagination.KeysetCursor;

/** Criteria predicates for public event search; filter methods return null when the filter is absent. */
final class EventSpecifications {

    /** Only published events still running or upcoming. */
    static Specification<Event> listable(Instant now) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EventStatus.PUBLISHED),
                cb.isNull(root.get("deletedAt")),
                cb.greaterThanOrEqualTo(root.get("endsAt"), now));
    }

    static Specification<Event> category(UUID categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    static Specification<Event> startsFrom(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    static Specification<Event> startsTo(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), to);
    }

    static Specification<Event> titleContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }

    /** Keyset predicate for ascending (startsAt, id) ordering. */
    static Specification<Event> afterCursor(KeysetCursor.Position cursor) {
        if (cursor == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.greaterThan(root.get("startsAt"), cursor.timestamp()),
                cb.and(cb.equal(root.get("startsAt"), cursor.timestamp()),
                        cb.greaterThan(root.get("id"), cursor.id())));
    }

    private EventSpecifications() {
    }
}
