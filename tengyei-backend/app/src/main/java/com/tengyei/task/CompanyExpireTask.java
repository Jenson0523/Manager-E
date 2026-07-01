package com.tengyei.task;

import com.tengyei.common.service.CompanyBlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyExpireTask {

    private final JdbcTemplate jdbcTemplate;
    private final Optional<CompanyBlockService> companyBlockService;

    @Scheduled(cron = "${tengyei.task.company-expire-cron:0 0 1 * * ?}")
    public void disableExpiredCompanies() {
        List<Long> expiredIds = jdbcTemplate.queryForList(
            "SELECT id FROM company WHERE is_deleted = 0 AND status = 1 " +
            "AND expire_date IS NOT NULL AND expire_date < CURRENT_DATE()",
            Long.class
        );
        if (expiredIds.isEmpty()) return;

        int affected = jdbcTemplate.update(
            "UPDATE company SET status = 2 " +
            "WHERE is_deleted = 0 AND status = 1 AND expire_date IS NOT NULL AND expire_date < CURRENT_DATE()"
        );
        companyBlockService.ifPresent(svc -> expiredIds.forEach(svc::block));
        log.info("公司到期自动停用任务完成，共停用 {} 家已到期企业", affected);
    }
}
