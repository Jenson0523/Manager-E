package com.tengyei.org.service;

import com.tengyei.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** 审批人解析：根据 config_json 节点的 approverType 找到实际审批人 */
@Service
@RequiredArgsConstructor
public class ApprovalResolverService {

    private final JdbcTemplate jdbcTemplate;

    public record Approver(Long id, String name) {}

    /**
     * 解析节点全部审批人。空列表 = 无可用审批人(SELF_APPROVE 或解析失败),
     * 由引擎决定跳过或拦截——中途解析失败不能把审批人的"通过"操作打断。
     * ROLE 返回角色下所有有效用户（配合 ALL 会签 / ANYONE 或签）；其余类型单人。
     */
    public List<Approver> resolveAll(String approverType, Long targetUserId, Long targetRoleId, Long applicantId) {
        if ("SELF_APPROVE".equals(approverType)) return List.of();

        if ("ROLE".equals(approverType)) {
            List<Approver> members = jdbcTemplate.query(
                "SELECT u.id, u.real_name FROM user_role ur JOIN `user` u ON u.id = ur.user_id " +
                "WHERE ur.role_id = ? AND u.is_deleted = 0 AND u.status = 1 ORDER BY u.id",
                (rs, i) -> new Approver(rs.getLong("id"), rs.getString("real_name")), targetRoleId);
            return members.stream().map(this::redirectByDelegate).toList();
        }

        Long approverId = switch (approverType) {
            case "LEADER" -> queryLong("SELECT leader_id FROM `user` WHERE id = ?", applicantId);
            case "DEPT_LEADER" -> {
                Long deptId = queryLong("SELECT dept_id FROM `user` WHERE id = ?", applicantId);
                yield deptId == null ? null : queryLong("SELECT leader_id FROM dept WHERE id = ?", deptId);
            }
            case "SPECIFIC_USER" -> targetUserId;
            default -> throw new BusinessException(422, "未知审批人类型：" + approverType);
        };
        if (approverId == null) return List.of();

        List<String> names = jdbcTemplate.queryForList(
            "SELECT real_name FROM `user` WHERE id = ? AND is_deleted = 0", String.class, approverId);
        if (names.isEmpty()) return List.of();
        return List.of(redirectByDelegate(new Approver(approverId, names.get(0))));
    }

    /** 委托生效期内重定向到代理人(单跳,不递归,避免代理链) */
    private Approver redirectByDelegate(Approver original) {
        List<Approver> rows = jdbcTemplate.query(
            "SELECT delegate_id, delegate_name FROM wf_delegate " +
            "WHERE tenant_id = ? AND owner_id = ? AND status = 1 AND is_deleted = 0 " +
            "AND start_at <= NOW() AND end_at >= NOW() LIMIT 1",
            (rs, i) -> new Approver(rs.getLong("delegate_id"), rs.getString("delegate_name")),
            com.tengyei.common.context.TenantContext.getTenantId(), original.id());
        if (rows.isEmpty()) return original;
        Approver d = rows.get(0);
        return new Approver(d.id(), d.name() + "(代理:" + original.name() + ")");
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
