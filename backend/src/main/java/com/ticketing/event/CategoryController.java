package com.ticketing.event;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.event.dto.CategoryResponse;

@RestController
@RequestMapping("/categories")
class CategoryController {

    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    List<CategoryResponse> listCategories() {
        return categoryService.listActive().stream().map(CategoryResponse::from).toList();
    }
}
