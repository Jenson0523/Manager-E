package com.tengyei.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日志保留自动清理任务：每日删除超过保留期的操作日志和登录日志。
 * 保留天数读取平台级配置 data.log_retention_days（默认 730 天），
 * cron 可通过 tengyei.task.log-clean-cron 覆盖，默认每天凌晨 2 点执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanTask {

    private static final long DEFAULT_RETENTION_DAYS = 730L;

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${tengyei.task.log-clean-cron:0 0 2 * * ?}")
    public void cleanExpiredLogs() {
        long retentionDays = resolveRetentionDays();
        if (retentionDays <= 0) return;

        int auditDeleted = jdbcTemplate.update(
            "DELETE FROM audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)", retentionDays);
        int loginDeleted = jdbcTemplate.update(
            "DELETE FROM login_log WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)", retentionDays);

        if (auditDeleted > 0 || loginDeleted > 0) {
            log.info("日志清理任务完成（保留 {} 天）：删除操作日志 {} 条，登录日志 {} 条",
                retentionDays, auditDeleted, loginDeleted);
        }
    }

    private long resolveRetentionDays() {
        try {
            List<String> values = jdbcTemplate.queryForList(
                "SELECT config_value FROM system_config WHERE tenant_id = 0 AND config_key = 'data.log_retention_days'",
                String.class);
            if (!values.isEmpty()) {
                return Long.parseLong(values.get(0).trim());
            }
        } catch (Exception e) {
            log.warn("读取日志保留天数配置失败，使用默认值 {} 天", DEFAULT_RETENTION_DAYS, e);
        }
        return DEFAULT_RETENTION_DAYS;
    }
}
