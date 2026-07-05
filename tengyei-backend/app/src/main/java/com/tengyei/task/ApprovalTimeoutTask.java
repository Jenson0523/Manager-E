package com.tengyei.task;

import com.tengyei.org.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 审批超时提醒:每小时扫描已过 due_at 的待办节点,给审批人发站内消息(每节点只提醒一次) */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutTask {

    private final JdbcTemplate jdbcTemplate;
    private final NoticeService noticeService;

    @Scheduled(cron = "${tengyei.task.approval-timeout-cron:0 0 * * * ?}")
    public void remindTimeoutNodes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT n.id, n.tenant_id, n.approver_id, n.instance_id, i.instance_no " +
            "FROM wf_node n JOIN wf_instance i ON i.id = n.instance_id " +
            "WHERE n.status = 'APPROVING' AND n.due_at IS NOT NULL AND n.due_at < NOW()");
        int sent = 0;
        for (Map<String, Object> r : rows) {
            Long nodeId = ((Number) r.get("id")).longValue();
            if (noticeService.timeoutNoticeExists(nodeId)) continue;
            noticeService.send(
                ((Number) r.get("tenant_id")).longValue(),
                ((Number) r.get("approver_id")).longValue(),
                "APPROVAL_TIMEOUT", "审批已超时",
                "审批 " + r.get("instance_no") + " 已超过处理时限,请尽快处理",
                "wf_node", nodeId);
            sent++;
        }
        if (sent > 0) log.info("审批超时提醒任务完成,发送 {} 条", sent);
    }
}
