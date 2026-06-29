package com.tengyei.controller;

import com.alibaba.excel.EasyExcel;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.dto.LoginLogExportVO;
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
@RequestMapping("/api/v1/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:view')")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "result", required = false) Integer result,
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
        if (username != null && !username.isBlank()) {
            where.append(" AND username LIKE ?");
            params.add("%" + username + "%");
        }
        if (result != null) {
            where.append(" AND result = ?");
            params.add(result);
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
            "SELECT COUNT(*) FROM login_log " + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) (page - 1) * size);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "SELECT id, tenant_id AS tenantId, user_id AS userId, username, " +
            "login_type AS loginType, ip_address AS ipAddress, " +
            "result, fail_reason AS failReason, created_at AS createdAt" +
            " FROM login_log " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            pageParams.toArray());

        return Result.ok(PageResult.of(records, total, page, size));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:view')")
    public void export(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "result", required = false) Integer result,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {

        boolean isSuperAdmin = TenantContext.isSuperAdmin();
        Long tenantId = TenantContext.getTenantId();

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");

        if (!isSuperAdmin) { where.append(" AND tenant_id = ?"); params.add(tenantId); }
        if (username != null && !username.isBlank()) { where.append(" AND username LIKE ?"); params.add("%" + username + "%"); }
        if (result != null) { where.append(" AND result = ?"); params.add(result); }
        if (startDate != null) { where.append(" AND created_at >= ?"); params.add(startDate.atStartOfDay()); }
        if (endDate != null) { where.append(" AND created_at < ?"); params.add(endDate.plusDays(1).atStartOfDay()); }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT username, login_type, ip_address, result, fail_reason, created_at " +
            "FROM login_log " + where + " ORDER BY created_at DESC LIMIT 5000",
            params.toArray());

        List<LoginLogExportVO> data = rows.stream().map(row -> {
            LoginLogExportVO vo = new LoginLogExportVO();
            vo.setUsername((String) row.get("username"));
            vo.setLoginType((String) row.get("login_type"));
            vo.setIpAddress((String) row.get("ip_address"));
            Object res = row.get("result");
            vo.setResult(Integer.valueOf(1).equals(res) ? "成功" : "失败");
            vo.setFailReason((String) row.get("fail_reason"));
            Object ca = row.get("created_at");
            vo.setCreatedAt(ca != null ? ca.toString() : "");
            return vo;
        }).toList();

        String fileName = URLEncoder.encode("登录日志_" + LocalDate.now(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        EasyExcel.write(response.getOutputStream(), LoginLogExportVO.class)
            .sheet("登录日志").doWrite(data);
    }
}
