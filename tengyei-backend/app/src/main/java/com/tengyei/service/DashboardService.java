package com.tengyei.service;

import com.tengyei.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        if (TenantContext.isSuperAdmin()) {
            data.put("scope", "super");
            data.put("companyTotal", count("SELECT COUNT(*) FROM company WHERE is_deleted = 0"));
            data.put("companyActive", count(
                "SELECT COUNT(*) FROM company WHERE is_deleted = 0 AND status = 1"));
            data.put("companyTodayNew", count(
                "SELECT COUNT(*) FROM company WHERE is_deleted = 0 AND created_at >= CURRENT_DATE"));
            data.put("userTotal", count(
                "SELECT COUNT(*) FROM user WHERE is_deleted = 0 AND is_super_admin = 0"));
            List<Map<String, Object>> recent = jdbcTemplate.queryForList(
                "SELECT id, company_no AS companyNo, full_name AS fullName, short_name AS shortName, " +
                "status, created_at AS createdAt " +
                "FROM company WHERE is_deleted = 0 ORDER BY id DESC LIMIT 5");
            data.put("recentCompanies", recent);
        } else {
            Long tenantId = TenantContext.getTenantId();
            data.put("scope", "company");
            data.put("deptCount", count(
                "SELECT COUNT(*) FROM dept WHERE is_deleted = 0 AND tenant_id = ?", tenantId));
            data.put("branchCount", count(
                "SELECT COUNT(*) FROM branch WHERE is_deleted = 0 AND tenant_id = ?", tenantId));
            data.put("userCount", count(
                "SELECT COUNT(*) FROM user WHERE is_deleted = 0 AND is_super_admin = 0 AND tenant_id = ?",
                tenantId));
            data.put("todayLoginCount", count(
                "SELECT COUNT(*) FROM user WHERE is_deleted = 0 AND tenant_id = ? " +
                "AND last_login_at >= CURRENT_DATE", tenantId));
        }
        return data;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n != null ? n : 0L;
    }
}
