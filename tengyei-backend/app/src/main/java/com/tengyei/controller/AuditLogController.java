package com.tengyei.controller;

import com.alibaba.excel.EasyExcel;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.dto.AuditLogExportVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:view') or hasAuthority('PERM_platform:audit:view')")
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

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:export') or hasAuthority('PERM_platform:audit:view')")
    public void export(
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {

        boolean isSuperAdmin = TenantContext.isSuperAdmin();
        Long tenantId = TenantContext.getTenantId();

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");

        if (!isSuperAdmin) { where.append(" AND tenant_id = ?"); params.add(tenantId); }
        if (module != null && !module.isBlank()) { where.append(" AND module = ?"); params.add(module); }
        if (startDate != null) { where.append(" AND created_at >= ?"); params.add(startDate.atStartOfDay()); }
        if (endDate != null) { where.append(" AND created_at < ?"); params.add(endDate.plusDays(1).atStartOfDay()); }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT user_name, module, action_type, description, ip_address, result, error_msg, created_at " +
            "FROM audit_log " + where + " ORDER BY created_at DESC LIMIT 5000",
            params.toArray());

        List<AuditLogExportVO> data = rows.stream().map(row -> {
            AuditLogExportVO vo = new AuditLogExportVO();
            vo.setUserName((String) row.get("user_name"));
            vo.setModule((String) row.get("module"));
            vo.setActionType((String) row.get("action_type"));
            vo.setDescription((String) row.get("description"));
            vo.setIpAddress((String) row.get("ip_address"));
            Object res = row.get("result");
            vo.setResult(Integer.valueOf(1).equals(res) ? "成功" : "失败");
            vo.setErrorMsg((String) row.get("error_msg"));
            Object ca = row.get("created_at");
            vo.setCreatedAt(ca != null ? ca.toString() : "");
            return vo;
        }).toList();

        String fileName = URLEncoder.encode("操作日志_" + LocalDate.now(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        EasyExcel.write(response.getOutputStream(), AuditLogExportVO.class)
            .sheet("操作日志").doWrite(data);
    }
}
