package com.tengyei.controller;

import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:view')")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        boolean isSuperAdmin = TenantContext.isSuperAdmin();
        Long tenantId = TenantContext.getTenantId();

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");

        if (!isSuperAdmin) {
            where.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (module != null && !module.isBlank()) {
            where.append(" AND module = ?");
            params.add(module);
        }
        if (startDate != null) {
            where.append(" AND created_at >= ?");
            params.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            where.append(" AND created_at < ?");
            params.add(endDate.plusDays(1).atStartOfDay());
        }

        long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log " + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) (page - 1) * size);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "SELECT id, tenant_id AS tenantId, user_id AS userId, user_name AS userName, " +
            "module, action_type AS actionType, description, ip_address AS ipAddress, " +
            "user_agent AS userAgent, result, error_msg AS errorMsg, created_at AS createdAt" +
            " FROM audit_log " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            pageParams.toArray());

        return Result.ok(PageResult.of(records, total, page, size));
    }
}
