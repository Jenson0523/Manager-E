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

        // 关联 user 只提醒在职启用的负责人:发给已离职/停用账号等于没人管,不如显式告警
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT c.id, c.tenant_id, c.owner_id, c.contract_no, c.name, c.end_date, " +
            "       u.id AS owner_alive " +
            "FROM biz_contract c " +
            "LEFT JOIN `user` u ON u.id = c.owner_id AND u.is_deleted = 0 AND u.status = 1 " +
            "WHERE c.is_deleted = 0 AND c.status IN ('EFFECTIVE', 'PERFORMING') " +
            "AND c.owner_id IS NOT NULL AND c.end_date IS NOT NULL AND c.end_date <= ? " +
            "AND (c.expire_notified_on IS NULL OR c.expire_notified_on < ?)",
            deadline, today);

        int sent = 0;
        int orphaned = 0;
        for (Map<String, Object> r : rows) {
            // 单行出错不能拖垮整轮提醒(否则当天所有租户一条都发不出)
            try {
                Long id = ((Number) r.get("id")).longValue();
                LocalDate endDate = toLocalDate(r.get("end_date"));
                if (endDate == null) {
                    log.warn("合同到期提醒跳过:end_date 无法解析, contractId={}, raw={}", id, r.get("end_date"));
                    continue;
                }
                if (r.get("owner_alive") == null) {
                    orphaned++;
                    log.warn("合同到期提醒跳过:负责人已离职或停用, contractId={}, contractNo={}, ownerId={}",
                        id, r.get("contract_no"), r.get("owner_id"));
                    continue;
                }

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
            } catch (Exception e) {
                log.warn("合同到期提醒单条失败, row={}", r.get("id"), e);
            }
        }
        if (sent > 0) log.info("合同到期提醒任务完成,发送 {} 条", sent);
        if (orphaned > 0) log.warn("有 {} 份临期合同的负责人已离职/停用,需重新指派", orphaned);
    }

    /** DATE 列的返回类型随驱动与连接参数而异,统一容错转换,避免强转炸掉整轮任务 */
    private LocalDate toLocalDate(Object raw) {
        if (raw instanceof java.sql.Date d) return d.toLocalDate();
        if (raw instanceof LocalDate ld) return ld;
        if (raw instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        if (raw == null) return null;
        try {
            return LocalDate.parse(String.valueOf(raw).substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
