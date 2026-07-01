package com.tengyei.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 公司到期自动停用任务：每日扫描已到期但仍处于启用状态的公司，将其状态置为停用。
 * cron 表达式可通过配置项 tengyei.task.company-expire-cron 覆盖，默认每天凌晨 1 点执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyExpireTask {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${tengyei.task.company-expire-cron:0 0 1 * * ?}")
    public void disableExpiredCompanies() {
        int affected = jdbcTemplate.update(
            "UPDATE company SET status = 2 " +
            "WHERE is_deleted = 0 AND status = 1 AND expire_date IS NOT NULL AND expire_date < CURRENT_DATE()"
        );
        if (affected > 0) {
            log.info("公司到期自动停用任务完成，共停用 {} 家已到期企业", affected);
        }
    }
}
