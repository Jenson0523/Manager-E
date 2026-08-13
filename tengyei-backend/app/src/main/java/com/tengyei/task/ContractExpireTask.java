package com.tengyei.task;

import com.tengyei.org.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 合同到期提醒:每日扫描 N 天内到期(含已过期)的履约中合同,给负责人发站内消息。
 * 用 expire_notified_on 做当日去重,改了到期日的合同会在保存时清空该标记从而重新提醒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpireTask {

    private final JdbcTemplate jdbcTemplate;
    private final NoticeService noticeService;

    @Value("${tengyei.contract.expire-remind-days:30}")
    private int expireRemindDays;

    @Scheduled(cron = "${tengyei.task.contract-expire-cron:0 30 8 * * ?}")
    public void remindExpiringContracts() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(expireRemindDays);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, tenant_id, owner_id, contract_no, name, end_date " +
            "FROM biz_contract " +
            "WHERE is_deleted = 0 AND status IN ('EFFECTIVE', 'PERFORMING') " +
            "AND owner_id IS NOT NULL AND end_date IS NOT NULL AND end_date <= ? " +
            "AND (expire_notified_on IS NULL OR expire_notified_on < ?)",
            deadline, today);

        int sent = 0;
        for (Map<String, Object> r : rows) {
            Long id = ((Number) r.get("id")).longValue();
            LocalDate endDate = ((java.sql.Date) r.get("end_date")).toLocalDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
            String content = days < 0
                ? "合同 " + r.get("contract_no") + "「" + r.get("name") + "」已于 " + endDate + " 到期,请及时续签或终止"
                : "合同 " + r.get("contract_no") + "「" + r.get("name") + "」将于 " + endDate
                  + " 到期(剩余 " + days + " 天),请及时处理";

            noticeService.send(
                ((Number) r.get("tenant_id")).longValue(),
                ((Number) r.get("owner_id")).longValue(),
                "CONTRACT_EXPIRE",
                days < 0 ? "合同已到期" : "合同即将到期",
                content, "contract", id);

            jdbcTemplate.update(
                "UPDATE biz_contract SET expire_notified_on = ? WHERE id = ?", today, id);
            sent++;
        }
        if (sent > 0) log.info("合同到期提醒任务完成,发送 {} 条", sent);
    }
}
