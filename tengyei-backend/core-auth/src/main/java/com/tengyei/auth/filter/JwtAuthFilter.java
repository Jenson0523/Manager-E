package com.tengyei.auth.filter;

import com.tengyei.auth.service.JwtService;
import com.tengyei.auth.service.TokenBlacklistService;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.service.CompanyBlockService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final Optional<TokenBlacklistService> blacklistService;
    private final Optional<CompanyBlockService> companyBlockService;

    @Autowired
    public JwtAuthFilter(JwtService jwtService,
                         JdbcTemplate jdbcTemplate,
                         Optional<TokenBlacklistService> blacklistService,
                         Optional<CompanyBlockService> companyBlockService) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.blacklistService = blacklistService;
        this.companyBlockService = companyBlockService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            boolean blacklisted = blacklistService.map(svc -> svc.isBlacklisted(token)).orElse(false);
            if (token != null && jwtService.isValid(token) && !blacklisted) {
                Long tenantId = jwtService.getTenantId(token);
                Long userId = jwtService.getUserId(token);
                Long branchId = jwtService.getBranchId(token);

                if (tenantId != null && tenantId != 0L
                        && companyBlockService.map(svc -> svc.isBlocked(tenantId)).orElse(false)) {
                    // 公司已停用/到期，拒绝请求
                    response.setStatus(423);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":423,\"message\":\"所属企业已停用，请联系平台管理员\"}");
                    return;
                }
                String dataScope = jwtService.getDataScope(token);
                String realName = jwtService.getRealName(token);
                // 鉴权与界面同源:权限每请求实时查库,不再用登录时冻结在 JWT 里的快照。
                // 管理员改权限立刻生效,停用/删除的用户立刻失权。
                // ponytail: 每请求+2条索引查询,QPS 上来后给此处加短TTL本地缓存
                List<String> permissions = loadPermissions(userId);

                TenantContext.setTenantId(tenantId);
                TenantContext.setUserId(userId);
                TenantContext.setBranchId(branchId);
                TenantContext.setDataScope(dataScope);
                TenantContext.setUserName(realName);

                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                        .collect(Collectors.toList());

                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignored) {
            // Invalid token — filter silently; endpoint will return 401
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private List<String> loadPermissions(Long userId) {
        var rows = jdbcTemplate.queryForList(
            "SELECT is_super_admin, tenant_id FROM `user` WHERE id = ? AND is_deleted = 0 AND status = 1",
            userId);
        if (rows.isEmpty()) return List.of();
        Object su = rows.get(0).get("is_super_admin");
        if (su != null && ((Number) su).intValue() == 1) return List.of("*");
        Long tenantId = ((Number) rows.get(0).get("tenant_id")).longValue();
        // r.tenant_id 必须等于用户所属公司:兜底防线,即使历史数据里被挂了
        // 平台/其他公司的角色,鉴权时也不生效(写入侧 assignRoles 已同源校验)
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT p.code FROM permission p " +
            "JOIN role_permission rp ON rp.permission_id = p.id " +
            "JOIN user_role ur ON ur.role_id = rp.role_id " +
            "JOIN role r ON r.id = ur.role_id AND r.status = 1 AND r.is_deleted = 0 AND r.tenant_id = ? " +
            "WHERE ur.user_id = ? AND p.status = 1",
            String.class, tenantId, userId);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
