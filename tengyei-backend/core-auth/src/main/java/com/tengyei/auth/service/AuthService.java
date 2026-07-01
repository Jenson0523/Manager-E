package com.tengyei.auth.service;

import com.tengyei.auth.dto.LoginRequest;
import com.tengyei.auth.dto.LoginResponse;
import com.tengyei.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private final JwtService jwtService;
    private final Optional<TokenBlacklistService> blacklistService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AuthService(JwtService jwtService,
                       Optional<TokenBlacklistService> blacklistService,
                       PasswordEncoder passwordEncoder,
                       JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 15;

    public LoginResponse login(LoginRequest req, String clientIp) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, tenant_id, username, password, real_name, " +
            "is_super_admin, status, pwd_reset_required, " +
            "login_fail_count, locked_until, branch_id " +
            "FROM `user` WHERE username = ? AND is_deleted = 0",
            req.getUsername()
        );

        if (rows.isEmpty()) {
            writeLoginLog(0L, 0L, req.getUsername(), clientIp, 0, "用户名不存在");
            throw new BusinessException(401, "用户名或密码错误");
        }

        Map<String, Object> row = rows.get(0);
        Long userId = toLong(row.get("id"));
        Long tenantId = toLong(row.get("tenant_id"));
        String encodedPwd = (String) row.get("password");
        int status = toInt(row.get("status"));
        int failCount = toInt(row.get("login_fail_count"));
        Object lockedUntilObj = row.get("locked_until");
        boolean pwdResetRequired = toInt(row.get("pwd_reset_required")) == 1;
        boolean isSuperAdmin = toInt(row.get("is_super_admin")) == 1;

        // Check lock
        if (lockedUntilObj != null) {
            LocalDateTime lockedUntil = lockedUntilObj instanceof LocalDateTime ldt
                    ? ldt : LocalDateTime.parse(lockedUntilObj.toString().replace(" ", "T"));
            if (LocalDateTime.now().isBefore(lockedUntil)) {
                writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 0, "账号已锁定");
                throw new BusinessException(423, "账号已锁定，请 " + LOCK_MINUTES + " 分钟后重试");
            }
        }

        if (status == 0) {
            writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 0, "账号已停用");
            throw new BusinessException(423, "账号已停用，请联系管理员");
        }

        if (!passwordEncoder.matches(req.getPassword(), encodedPwd)) {
            int newFailCount = failCount + 1;
            if (newFailCount >= MAX_FAIL_COUNT) {
                jdbcTemplate.update(
                    "UPDATE `user` SET login_fail_count = ?, locked_until = ? WHERE id = ?",
                    newFailCount, LocalDateTime.now().plusMinutes(LOCK_MINUTES), userId
                );
                writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 0, "密码错误次数过多，账号已锁定");
                throw new BusinessException(423, "密码错误次数过多，账号已锁定 " + LOCK_MINUTES + " 分钟");
            }
            jdbcTemplate.update("UPDATE `user` SET login_fail_count = ? WHERE id = ?", newFailCount, userId);
            writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 0, "密码错误");
            throw new BusinessException(401, "用户名或密码错误，还可尝试 " + (MAX_FAIL_COUNT - newFailCount) + " 次");
        }

        // Check company status only for company-tier users (tenant_id != 0)
        if (!isSuperAdmin && tenantId != null && tenantId != 0L) {
            List<Map<String, Object>> companyRows = jdbcTemplate.queryForList(
                "SELECT status, expire_date FROM company WHERE id = ? AND is_deleted = 0", tenantId
            );
            if (companyRows.isEmpty() || toInt(companyRows.get(0).get("status")) != 1) {
                writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 0, "所属企业已停用");
                throw new BusinessException(423, "所属企业已停用，请联系平台管理员");
            }
            Object expireObj = companyRows.get(0).get("expire_date");
            if (expireObj instanceof java.sql.Date sqlDate
                && sqlDate.toLocalDate().isBefore(java.time.LocalDate.now())) {
                writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 0, "所属企业已到期");
                throw new BusinessException(423, "所属企业使用期限已到期，请联系平台管理员");
            }
        }

        // Reset fail count, update last login
        jdbcTemplate.update(
            "UPDATE `user` SET login_fail_count = 0, locked_until = NULL, " +
            "last_login_at = NOW(), last_login_ip = ? WHERE id = ?",
            clientIp, userId
        );

        // Build roles and permissions
        List<String> roleCodes;
        List<String> permissions;
        String dataScope;
        Long branchId = row.get("branch_id") != null ? toLong(row.get("branch_id")) : null;

        if (isSuperAdmin) {
            roleCodes = List.of("super_admin");
            permissions = List.of("*");
            dataScope = "all";
        } else {
            roleCodes = jdbcTemplate.queryForList(
                "SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id " +
                "WHERE ur.user_id = ? AND r.status = 1 AND r.is_deleted = 0",
                String.class, userId
            );
            permissions = jdbcTemplate.queryForList(
                "SELECT DISTINCT p.code FROM permission p " +
                "JOIN role_permission rp ON rp.permission_id = p.id " +
                "JOIN user_role ur ON ur.role_id = rp.role_id " +
                "WHERE ur.user_id = ? AND p.status = 1",
                String.class, userId
            );
            String scope = jdbcTemplate.queryForObject(
                "SELECT MIN(r.data_scope) FROM role r " +
                "JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId
            );
            dataScope = scope != null ? scope : "self";
        }

        String token = jwtService.generate(tenantId, userId, branchId, roleCodes, permissions, dataScope,
                (String) row.get("real_name"));
        writeLoginLog(userId, tenantId, req.getUsername(), clientIp, 1, null);

        return LoginResponse.builder()
                .accessToken(token)
                .expiresIn(7200L)
                .pwdResetRequired(pwdResetRequired)
                .realName((String) row.get("real_name"))
                .tenantId(tenantId)
                .build();
    }

    private void writeLoginLog(Long userId, Long tenantId, String username, String ip, int result, String failReason) {
        try {
            jdbcTemplate.update(
                "INSERT INTO login_log (tenant_id, user_id, username, login_type, ip_address, result, fail_reason) " +
                "VALUES (?, ?, ?, 'login', ?, ?, ?)",
                tenantId != null ? tenantId : 0L,
                userId != null ? userId : 0L,
                username, ip, result, failReason);
        } catch (Exception ex) {
            log.warn("Failed to write login log: {}", ex.getMessage());
        }
    }

    public void logout(String token) {
        if (jwtService.isValid(token)) {
            blacklistService.ifPresent(svc -> svc.blacklist(token, 7200L));
        }
    }

    public String refresh(String token) {
        boolean blacklisted = blacklistService.map(svc -> svc.isBlacklisted(token)).orElse(false);
        if (!jwtService.isValid(token) || blacklisted) {
            throw new BusinessException(401, "Token 无效或已过期");
        }
        if (!jwtService.shouldRefresh(token)) {
            return token;
        }
        blacklistService.ifPresent(svc -> svc.blacklist(token, 7200L));
        Long tenantId = jwtService.getTenantId(token);
        Long userId = jwtService.getUserId(token);
        Long branchId = jwtService.getBranchId(token);
        String dataScope = jwtService.getDataScope(token);
        List<String> roleCodes = jdbcTemplate.queryForList(
            "SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = ? AND r.status = 1",
            String.class, userId
        );
        // Re-query permissions fresh from DB instead of carrying from old token
        List<String> permissions = jdbcTemplate.queryForList(
            "SELECT DISTINCT p.code FROM permission p " +
            "JOIN role_permission rp ON rp.permission_id = p.id " +
            "JOIN user_role ur ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = ? AND p.status = 1",
            String.class, userId
        );
        String realName = jwtService.getRealName(token);
        return jwtService.generate(tenantId, userId, branchId, roleCodes, permissions, dataScope, realName);
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
