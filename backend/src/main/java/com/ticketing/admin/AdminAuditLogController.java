package com.ticketing.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.admin.dto.AuditLogResponse;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;

@RestController
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
class AdminAuditLogController {

    private static final int DEFAULT_PAGE = 50;
    private static final int MAX_PAGE = 200;

    private final AdminAuditLogService auditLogService;

    AdminAuditLogController(AdminAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, DEFAULT_PAGE, MAX_PAGE);
        List<AuditLogResponse> rows = auditLogService.listLogs(KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, l -> KeysetCursor.encode(l.createdAt(), l.logId()));
    }
}
