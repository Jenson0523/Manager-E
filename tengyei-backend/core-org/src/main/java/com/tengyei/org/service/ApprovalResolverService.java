package com.tengyei.org.service;

import com.tengyei.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** 审批人解析：根据 config_json 节点的 approverType 找到实际审批人（Phase 1 仅支持单人/FIRST，会签/或签留待 Phase 2） */
@Service
@RequiredArgsConstructor
public class ApprovalResolverService {

    private final JdbcTemplate jdbcTemplate;

    public record Approver(Long id, String name) {}

    /** 返回 null 表示 SELF_APPROVE（自动通过，无需真人审批） */
    public Approver resolve(String approverType, Long targetUserId, Long targetRoleId, Long applicantId) {
        if ("SELF_APPROVE".equals(approverType)) return null;

        Long approverId = switch (approverType) {
            case "LEADER" -> queryLong("SELECT leader_id FROM `user` WHERE id = ?", applicantId);
            case "DEPT_LEADER" -> {
                Long deptId = queryLong("SELECT dept_id FROM `user` WHERE id = ?", applicantId);
                yield deptId == null ? null : queryLong("SELECT leader_id FROM dept WHERE id = ?", deptId);
            }
            case "SPECIFIC_USER" -> targetUserId;
            case "ROLE" -> queryLong(
                "SELECT u.id FROM user_role ur JOIN `user` u ON u.id = ur.user_id " +
                "WHERE ur.role_id = ? AND u.is_deleted = 0 AND u.status = 1 LIMIT 1", targetRoleId);
            default -> throw new BusinessException(422, "未知审批人类型：" + approverType);
        };
        if (approverId == null) throw new BusinessException(422, "无法解析审批人（" + approverType + "）");

        List<String> names = jdbcTemplate.queryForList(
            "SELECT real_name FROM `user` WHERE id = ? AND is_deleted = 0", String.class, approverId);
        if (names.isEmpty()) throw new BusinessException(422, "审批人不存在或已离职");
        return new Approver(approverId, names.get(0));
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
