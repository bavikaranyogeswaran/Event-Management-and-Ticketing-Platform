package com.ticketing.event.dto;

import java.util.UUID;

import com.ticketing.event.Category;

public record CategoryResponse(UUID id, String name, String slug) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
    }
}
