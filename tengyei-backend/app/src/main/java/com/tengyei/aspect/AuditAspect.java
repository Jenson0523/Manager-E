package com.tengyei.aspect;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final JdbcTemplate jdbcTemplate;

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        int result = 1;
        try {
            Object ret = pjp.proceed();
            return ret;
        } catch (Throwable t) {
            result = 0;
            throw t;
        } finally {
            writeLog(auditable, result);
        }
    }

    private void writeLog(Auditable auditable, int result) {
        try {
            Long tenantId = TenantContext.getTenantId();
            Long userId = TenantContext.getUserId();
            String userName = TenantContext.getUserName();
            if (userName == null || userName.isBlank()) {
                userName = userId != null ? "user-" + userId : "unknown";
            }

            String ip = null;
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            }

            jdbcTemplate.update(
                "INSERT INTO audit_log (tenant_id, user_id, user_name, module, action_type, description, ip_address, result) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId != null ? tenantId : 0L,
                userId != null ? userId : 0L,
                userName,
                auditable.module(),
                auditable.actionType(),
                auditable.description(),
                ip,
                result);
        } catch (Exception ex) {
            log.warn("Failed to write audit log: {}", ex.getMessage());
        }
    }
}
