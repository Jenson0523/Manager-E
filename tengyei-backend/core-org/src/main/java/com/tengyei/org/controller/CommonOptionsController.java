package com.tengyei.org.controller;

import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.Result;
import com.tengyei.org.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全站下拉选项(仅 id+名称的精简名录)。
 * 审批抄送/转交/加签、部门负责人、通知受众等业务场景都要"选同事/选角色/选部门",
 * 这些是业务功能的内在需要,不应要求 user:view/role:view/dept:view 等管理权限,
 * 否则窄权限账号(如只配审批权限的出纳)一点选人就连弹"无权限访问"。
 * 只返回名称,不含手机号/邮箱等敏感字段,管理页面仍走各自带权限守卫的完整接口。
 */
@RestController
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
public class CommonOptionsController {

    private final JdbcTemplate jdbcTemplate;
    private final DeptService deptService;

    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> options() {
        Long tenantId = TenantContext.getTenantId();
        Map<String, Object> data = new HashMap<>();
        data.put("users", jdbcTemplate.queryForList(
            "SELECT id, real_name AS realName FROM `user` " +
            "WHERE tenant_id = ? AND status = 1 AND is_deleted = 0 ORDER BY id LIMIT 500",
            tenantId));
        data.put("roles", jdbcTemplate.queryForList(
            "SELECT id, name FROM role WHERE tenant_id = ? AND status = 1 AND is_deleted = 0 ORDER BY id",
            tenantId));
        data.put("depts", deptService.tree());
        if (tenantId != null && tenantId == 0L) {
            // 平台账号:通知受众等场景需要选企业
            data.put("companies", jdbcTemplate.queryForList(
                "SELECT id, COALESCE(short_name, full_name) AS name FROM company " +
                "WHERE status = 1 AND is_deleted = 0 ORDER BY id"));
        }
        return Result.ok(data);
    }
}
