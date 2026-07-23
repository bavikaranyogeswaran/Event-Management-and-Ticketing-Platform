package com.ticketing.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.admin.dto.UserSummaryResponse;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController {

    private static final int DEFAULT_PAGE = 50;
    private static final int MAX_PAGE = 100;

    private final AdminUserService adminUserService;

    AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    PageResponse<UserSummaryResponse> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, DEFAULT_PAGE, MAX_PAGE);
        List<UserSummaryResponse> rows = adminUserService.listUsers(KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, u -> KeysetCursor.encode(u.createdAt(), u.userId()));
    }
}
