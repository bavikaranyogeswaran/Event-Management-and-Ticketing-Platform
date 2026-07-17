package com.ticketing.event;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/** Turns a title into a URL-friendly slug, adding a short suffix when the base is taken. */
@Component
class SlugGenerator {

    private static final String SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    String generate(String title, Predicate<String> exists) {
        String base = slugify(title);
        if (base.isEmpty()) {
            base = "event";
        }
        String candidate = base;
        while (exists.test(candidate)) {
            candidate = base + "-" + randomSuffix();
        }
        return candidate;
    }

    private String slugify(String title) {
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-") // non-alphanumerics become hyphens
                .replaceAll("(^-+)|(-+$)", ""); // trim leading/trailing hyphens
        return slug.length() > 140 ? slug.substring(0, 140) : slug;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            sb.append(SUFFIX_CHARS.charAt(random.nextInt(SUFFIX_CHARS.length())));
        }
        return sb.toString();
    }
}
