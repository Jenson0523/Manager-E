package com.tengyei.controller;

import com.tengyei.auth.dto.UserInfoVO;
import com.tengyei.auth.service.JwtService;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserInfoController {

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;

    @GetMapping("/userinfo")
    public Result<UserInfoVO> userinfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : authHeader;
        Long userId = TenantContext.getUserId();
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        String dataScope = TenantContext.getDataScope();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT username, real_name, avatar_url, is_super_admin, pwd_reset_required FROM `user` WHERE id = ? AND is_deleted = 0",
            userId
        );
        if (rows.isEmpty()) throw new BusinessException(401, "用户不存在");
        Map<String, Object> row = rows.get(0);

        boolean isSuperAdmin = toInt(row.get("is_super_admin")) == 1;
        boolean pwdResetRequired = toInt(row.get("pwd_reset_required")) == 1;
        List<String> permissions = jwtService.getPermissions(token);
        List<String> roleCodes = jdbcTemplate.queryForList(
            "SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
            String.class, userId
        );

        boolean isPlatformTier = tenantId != null && tenantId == 0L;
        List<UserInfoVO.RouteVO> routes = isPlatformTier
                ? buildPlatformRoutes(permissions) : buildCompanyRoutes(permissions);

        // Query company info for enterprise tenant
        String companyName = null;
        String companyLogo = null;
        if (tenantId != null && tenantId > 0L) {
            List<Map<String, Object>> compRows = jdbcTemplate.queryForList(
                "SELECT full_name, logo_url FROM company WHERE id = ?", tenantId);
            if (!compRows.isEmpty()) {
                companyName = (String) compRows.get(0).get("full_name");
                companyLogo = (String) compRows.get(0).get("logo_url");
            }
        }

        return Result.ok(UserInfoVO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .branchId(branchId)
                .username((String) row.get("username"))
                .realName((String) row.get("real_name"))
                .avatarUrl((String) row.get("avatar_url"))
                .companyName(companyName)
                .companyLogo(companyLogo)
                .isSuperAdmin(isSuperAdmin)
                .dataScope(dataScope != null ? dataScope : "all")
                .roleCodes(roleCodes)
                .permissions(permissions)
                .routes(routes)
                .pwdResetRequired(pwdResetRequired)
                .build());
    }

    private List<UserInfoVO.RouteVO> buildPlatformRoutes(List<String> permissions) {
        boolean all = permissions != null && permissions.contains("*");
        List<UserInfoVO.RouteVO> routes = new ArrayList<>();
        if (all || has(permissions, "platform:dashboard:view")) routes.add(route("/dashboard", "工作台"));
        if (all || has(permissions, "platform:company:view"))   routes.add(route("/admin/companies", "企业管理"));
        if (all || has(permissions, "platform:user:view"))      routes.add(route("/admin/users", "平台人员"));
        if (all || has(permissions, "platform:role:view"))      routes.add(route("/admin/roles", "平台角色"));
        if (all || has(permissions, "platform:audit:view"))     routes.add(route("/admin/audit-logs", "操作日志"));
        if (all || has(permissions, "platform:config:view"))    routes.add(route("/admin/system-config", "系统设置"));
        if (all || hasAny(permissions, "platform:approval:view", "platform:approval:apply"))
            routes.add(route("/company/approval", "审批中心"));
        return routes;
    }

    private boolean has(List<String> permissions, String code) {
        return permissions != null && permissions.contains(code);
    }

    private List<UserInfoVO.RouteVO> buildCompanyRoutes(List<String> permissions) {
        List<UserInfoVO.RouteVO> routes = new ArrayList<>();
        routes.add(route("/dashboard", "工作台"));
        if (hasAny(permissions, "dept:view", "branch:view")) routes.add(route("/company/org", "组织管理"));
        if (hasAny(permissions, "user:view")) routes.add(route("/company/users", "人员管理"));
        if (hasAny(permissions, "role:view")) routes.add(route("/company/roles", "角色与权限"));
        if (hasAny(permissions, "log:view", "*")) routes.add(route("/admin/audit-logs", "操作日志"));
        if (hasAny(permissions, "approval:view", "approval:apply")) routes.add(route("/company/approval", "审批中心"));
        return routes;
    }

    private boolean hasAny(List<String> permissions, String... required) {
        if (permissions == null) return false;
        for (String r : required) if (permissions.contains(r)) return true;
        return false;
    }

    private UserInfoVO.RouteVO route(String path, String name) {
        return UserInfoVO.RouteVO.builder().path(path).name(name).build();
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
