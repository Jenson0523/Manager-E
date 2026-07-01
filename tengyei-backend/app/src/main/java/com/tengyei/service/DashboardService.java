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

    public Map<String, Object> chartData() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        Long tenantId = TenantContext.isSuperAdmin() ? null : TenantContext.getTenantId();

        // 近7天用户增长趋势（user_trend）
        String trendSql;
        List<Object> trendParams = new java.util.ArrayList<>();
        if (tenantId != null) {
            trendSql = "SELECT DATE(created_at) AS date, COUNT(*) AS count " +
                       "FROM `user` WHERE is_deleted=0 AND is_super_admin=0 AND tenant_id=? " +
                       "AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                       "GROUP BY DATE(created_at) ORDER BY date";
            trendParams.add(tenantId);
        } else {
            trendSql = "SELECT DATE(created_at) AS date, COUNT(*) AS count " +
                       "FROM `user` WHERE is_deleted=0 AND is_super_admin=0 " +
                       "AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                       "GROUP BY DATE(created_at) ORDER BY date";
        }
        List<Map<String, Object>> trendRows = jdbcTemplate.queryForList(trendSql, trendParams.toArray());
        result.put("userTrend", trendRows);

        // 用户状态分布（status_dist）
        String distSql;
        List<Object> distParams = new java.util.ArrayList<>();
        if (tenantId != null) {
            distSql = "SELECT status, COUNT(*) AS count FROM `user` " +
                      "WHERE is_deleted=0 AND is_super_admin=0 AND tenant_id=? " +
                      "GROUP BY status";
            distParams.add(tenantId);
        } else {
            distSql = "SELECT status, COUNT(*) AS count FROM `user` " +
                      "WHERE is_deleted=0 AND is_super_admin=0 " +
                      "GROUP BY status";
        }
        List<Map<String, Object>> distRows = jdbcTemplate.queryForList(distSql, distParams.toArray());
        List<Map<String, Object>> statusDist = distRows.stream().map(row -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            int s = ((Number) row.get("status")).intValue();
            item.put("name", s == 1 ? "启用" : "停用");
            item.put("value", row.get("count"));
            return item;
        }).toList();
        result.put("statusDist", statusDist);

        // 超管专有：各企业用户数（company_dist，仅 super admin）
        if (tenantId == null) {
            String compSql = "SELECT c.full_name AS company, COUNT(u.id) AS count " +
                             "FROM company c LEFT JOIN `user` u ON u.tenant_id=c.id " +
                             "AND u.is_deleted=0 AND u.is_super_admin=0 " +
                             "GROUP BY c.id, c.full_name ORDER BY count DESC LIMIT 10";
            List<Map<String, Object>> compRows = jdbcTemplate.queryForList(compSql);
            result.put("companyDist", compRows);
        }

        return result;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n != null ? n : 0L;
    }
}
