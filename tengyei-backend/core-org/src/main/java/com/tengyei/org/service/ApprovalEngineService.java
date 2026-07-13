package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.ApprovalApplyDTO;
import com.tengyei.org.dto.ApprovalInstanceVO;
import com.tengyei.org.dto.ApprovalNodeConfig;
import com.tengyei.org.entity.WfDefinition;
import com.tengyei.org.entity.WfDelegate;
import com.tengyei.org.entity.WfInstance;
import com.tengyei.org.entity.WfNode;
import com.tengyei.org.entity.WfRecord;
import com.tengyei.org.mapper.WfDefinitionMapper;
import com.tengyei.org.mapper.WfDelegateMapper;
import com.tengyei.org.mapper.WfInstanceMapper;
import com.tengyei.org.mapper.WfNodeMapper;
import com.tengyei.org.mapper.WfRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 审批引擎：发起、审批推进、待办/已办查询（Phase 1：单人节点，会签/或签留待 Phase 2） */
@Service
@RequiredArgsConstructor
public class ApprovalEngineService {

    private static final Pattern CONDITION_PATTERN =
        Pattern.compile("^form\\.(\\w+)\\s*(>=|<=|==|!=|>|<)\\s*(.+)$");

    private final WfDefinitionMapper definitionMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfNodeMapper nodeMapper;
    private final WfRecordMapper recordMapper;
    private final WfDelegateMapper delegateMapper;
    private final ApprovalResolverService resolverService;
    private final NoticeService noticeService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Long apply(ApprovalApplyDTO dto, Long applicantId, String applicantName) {
        Long tenantId = TenantContext.getTenantId();
        WfDefinition definition = definitionMapper.selectOne(
            new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getTenantId, tenantId)
                .eq(WfDefinition::getFormType, dto.getFormType())
                .eq(WfDefinition::getStatus, 1)
                .orderByDesc(WfDefinition::getIsDefault)
                .last("LIMIT 1"));
        if (definition == null) throw new BusinessException(422, "该表单类型未配置审批流程");

        List<ApprovalNodeConfig> nodes = parseNodes(definition.getConfigJson());
        List<ApprovalNodeConfig> eligible = nodes.stream()
            .filter(n -> evalCondition(n.getCondition(), dto.getFormData()))
            .sorted((a, b) -> a.getOrderBy() - b.getOrderBy())
            .toList();
        if (eligible.isEmpty()) throw new BusinessException(422, "没有满足条件的审批节点");

        WfInstance instance = new WfInstance();
        instance.setTenantId(tenantId);
        instance.setInstanceNo(genInstanceNo());
        instance.setDefinitionId(definition.getId());
        instance.setFormType(dto.getFormType());
        instance.setFormData(toJson(dto.getFormData()));
        instance.setApplicantId(applicantId);
        instance.setApplicantName(applicantName);
        instance.setStatus("PENDING");
        instance.setPriority(0);
        instanceMapper.insert(instance);

        int order = 0;
        for (ApprovalNodeConfig cfg : eligible) {
            WfNode node = new WfNode();
            node.setTenantId(tenantId);
            node.setInstanceId(instance.getId());
            node.setNodeKey(cfg.getKey());
            node.setNodeName(cfg.getName());
            node.setApproverType(cfg.getApproverType());
            node.setTargetUserId(cfg.getTargetUserId());
            node.setTargetRoleId(cfg.getTargetRoleId());
            node.setTimeoutHours(cfg.getTimeoutHours());
            node.setResolveMode(cfg.getResolveMode());
            node.setRejectPolicy(cfg.getRejectPolicy());
            node.setNodeOrder(order++);
            node.setStatus("WAITING");
            nodeMapper.insert(node);
        }

        writeRecord(instance.getId(), null, applicantId, applicantName, "APPLY", null, null, "PENDING");
        advance(instance, applicantId, true);
        return instance.getId();
    }

    @Transactional
    public void act(Long instanceId, String action, String comment, Long operatorId, String operatorName) {
        Long tenantId = TenantContext.getTenantId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null || !instance.getTenantId().equals(tenantId)) {
            throw new BusinessException(404, "审批实例不存在");
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new BusinessException(409, "该审批已结束，无法再操作");
        }

        // 会签/或签下同一节点 key 有多行（每审批人一行）
        List<WfNode> active = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
            .eq(WfNode::getInstanceId, instanceId)
            .eq(WfNode::getNodeKey, instance.getCurrentNode())
            .eq(WfNode::getStatus, "APPROVING"));
        if (active.isEmpty()) throw new BusinessException(409, "当前节点状态异常，请刷新重试");
        WfNode node = active.stream()
            .filter(n -> operatorId.equals(n.getApproverId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(403, "无权处理该审批"));

        String before = instance.getStatus();
        node.setResult(action);
        node.setComment(comment);
        node.setActionBy(operatorId);
        node.setActionAt(LocalDateTime.now());
        node.setStatus("REJECT".equals(action) ? "REJECTED" : "APPROVED");
        nodeMapper.updateById(node);

        if ("REJECT".equals(action)) {
            String policy = node.getRejectPolicy() != null ? node.getRejectPolicy() : "TERMINATE";
            switch (policy) {
                case "TO_INITIATOR" -> rejectToInitiator(instance, node, operatorName, comment);
                case "TO_PREV" -> rejectToPrev(instance, node, operatorName, comment);
                default -> rejectTerminate(instance, node, operatorName, comment);
            }
        } else {
            boolean siblingsPending = active.stream()
                .anyMatch(n -> !n.getId().equals(node.getId()));
            if ("ALL".equals(node.getResolveMode()) && siblingsPending) {
                // 会签：等其余审批人，停留本节点
            } else {
                if (siblingsPending) {
                    // 或签：一人通过，其余作废
                    jdbcTemplate.update(
                        "UPDATE wf_node SET status = 'CANCELED', result = 'SKIPPED' " +
                        "WHERE instance_id = ? AND node_key = ? AND status = 'APPROVING'",
                        instanceId, node.getNodeKey());
                }
                advance(instance, instance.getApplicantId(), false);
            }
        }
        writeRecord(instanceId, node.getId(), operatorId, operatorName, action, comment, before, instance.getStatus());
    }

    /** 驳回=整单终结(默认策略) */
    private void rejectTerminate(WfInstance instance, WfNode node, String operatorName, String comment) {
        instance.setStatus("REJECTED");
        instance.setCurrentNode(null);
        instanceMapper.updateById(instance);
        jdbcTemplate.update(
            "UPDATE wf_node SET status = 'CANCELED' WHERE instance_id = ? AND status IN ('WAITING','APPROVING')",
            instance.getId());
        noticeService.send(instance.getTenantId(), instance.getApplicantId(),
            "APPROVAL_RESULT", "审批被驳回",
            "你发起的审批 " + instance.getInstanceNo() + " 被 " + operatorName + " 驳回"
                + (comment != null && !comment.isBlank() ? "：" + comment : ""),
            "approval", instance.getId());
    }

    /** 驳回=退回发起人,可修改后重新提交(重新从头走流程) */
    private void rejectToInitiator(WfInstance instance, WfNode node, String operatorName, String comment) {
        instance.setStatus("RETURNED");
        instance.setCurrentNode(null);
        instanceMapper.updateById(instance);
        jdbcTemplate.update(
            "UPDATE wf_node SET status = 'CANCELED' WHERE instance_id = ? AND status IN ('WAITING','APPROVING')",
            instance.getId());
        noticeService.send(instance.getTenantId(), instance.getApplicantId(),
            "APPROVAL_RESULT", "审批被退回",
            "你发起的审批 " + instance.getInstanceNo() + " 被 " + operatorName + " 退回,可修改后重新提交"
                + (comment != null && !comment.isBlank() ? "：" + comment : ""),
            "approval", instance.getId());
    }

    /** 驳回=退回上一节点重审(上一节点通过后回到本节点);首节点无上一级时退回发起人 */
    private void rejectToPrev(WfInstance instance, WfNode node, String operatorName, String comment) {
        // 按 node_order 找本实例中当前组之前最近的一个已走过的节点组
        List<WfNode> all = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
            .eq(WfNode::getInstanceId, instance.getId())
            .orderByAsc(WfNode::getNodeOrder));
        WfNode prev = null;
        for (WfNode n : all) {
            if (n.getNodeOrder() < node.getNodeOrder()
                    && "APPROVED".equals(n.getStatus())
                    && !"AUTO".equals(n.getResult())
                    && (prev == null || n.getNodeOrder() > prev.getNodeOrder())) {
                prev = n;
            }
        }
        if (prev == null) {
            rejectToInitiator(instance, node, operatorName, comment);
            return;
        }
        // 作废本组剩余审批人,把 上一节点 + 本节点 各复制一份排到队尾重走
        jdbcTemplate.update(
            "UPDATE wf_node SET status = 'CANCELED', result = 'SKIPPED' " +
            "WHERE instance_id = ? AND node_key = ? AND status = 'APPROVING'",
            instance.getId(), node.getNodeKey());
        Integer maxOrder = jdbcTemplate.queryForObject(
            "SELECT MAX(node_order) FROM wf_node WHERE instance_id = ?", Integer.class, instance.getId());
        insertWaitingCopy(prev, maxOrder + 1);
        insertWaitingCopy(node, maxOrder + 2);
        noticeService.send(instance.getTenantId(), instance.getApplicantId(),
            "APPROVAL_RESULT", "审批被退回上一节点",
            "你发起的审批 " + instance.getInstanceNo() + " 被 " + operatorName + " 退回「" + prev.getNodeName() + "」重审"
                + (comment != null && !comment.isBlank() ? "：" + comment : ""),
            "approval", instance.getId());
        advance(instance, instance.getApplicantId(), false);
    }

    /** 复制一个节点的配置为全新 WAITING 行(审批人置空,推进时重新解析) */
    private void insertWaitingCopy(WfNode src, int order) {
        WfNode copy = new WfNode();
        copy.setTenantId(src.getTenantId());
        copy.setInstanceId(src.getInstanceId());
        copy.setNodeKey(src.getNodeKey());
        copy.setNodeName(src.getNodeName());
        copy.setApproverType(src.getApproverType());
        copy.setTargetUserId(src.getTargetUserId());
        copy.setTargetRoleId(src.getTargetRoleId());
        copy.setResolveMode(src.getResolveMode());
        copy.setTimeoutHours(src.getTimeoutHours());
        copy.setRejectPolicy(src.getRejectPolicy());
        copy.setNodeOrder(order);
        copy.setStatus("WAITING");
        nodeMapper.insert(copy);
    }

    /** 退回发起人后重新提交:可更新表单,按流程定义重建节点重新走 */
    @Transactional
    public void resubmit(Long instanceId, Map<String, Object> formData, Long operatorId, String operatorName) {
        Long tenantId = TenantContext.getTenantId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null || !instance.getTenantId().equals(tenantId)) {
            throw new BusinessException(404, "审批实例不存在");
        }
        if (!instance.getApplicantId().equals(operatorId)) {
            throw new BusinessException(403, "只能重新提交自己发起的审批");
        }
        if (!"RETURNED".equals(instance.getStatus())) {
            throw new BusinessException(409, "仅被退回的审批可重新提交");
        }
        if (formData != null) instance.setFormData(toJson(formData));

        WfDefinition definition = definitionMapper.selectById(instance.getDefinitionId());
        if (definition == null || definition.getStatus() != 1) {
            throw new BusinessException(422, "原审批流程已停用,请重新发起");
        }
        final Map<String, Object> effective = formData != null ? formData : parseFormData(instance.getFormData());
        List<ApprovalNodeConfig> eligible = parseNodes(definition.getConfigJson()).stream()
            .filter(n -> evalCondition(n.getCondition(), effective))
            .sorted((a, b) -> a.getOrderBy() - b.getOrderBy())
            .toList();
        if (eligible.isEmpty()) throw new BusinessException(422, "没有满足条件的审批节点");

        Integer maxOrder = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(node_order), -1) FROM wf_node WHERE instance_id = ?", Integer.class, instanceId);
        int order = maxOrder + 1;
        for (ApprovalNodeConfig cfg : eligible) {
            WfNode node = new WfNode();
            node.setTenantId(tenantId);
            node.setInstanceId(instanceId);
            node.setNodeKey(cfg.getKey());
            node.setNodeName(cfg.getName());
            node.setApproverType(cfg.getApproverType());
            node.setTargetUserId(cfg.getTargetUserId());
            node.setTargetRoleId(cfg.getTargetRoleId());
            node.setTimeoutHours(cfg.getTimeoutHours());
            node.setResolveMode(cfg.getResolveMode());
            node.setRejectPolicy(cfg.getRejectPolicy());
            node.setNodeOrder(order++);
            node.setStatus("WAITING");
            nodeMapper.insert(node);
        }
        instance.setStatus("PENDING");
        instanceMapper.updateById(instance);
        writeRecord(instanceId, null, operatorId, operatorName, "RESUBMIT", null, "RETURNED", "PENDING");
        advance(instance, operatorId, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFormData(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 加签:当前审批人插入一位审批人。PRE=先让其审再回到自己;POST=自己审完后其再审 */
    @Transactional
    public void addSign(Long instanceId, Long targetUserId, String position, Long operatorId, String operatorName) {
        Long tenantId = TenantContext.getTenantId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null || !instance.getTenantId().equals(tenantId)) {
            throw new BusinessException(404, "审批实例不存在");
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new BusinessException(409, "该审批已结束,无法加签");
        }
        if (!"PRE".equals(position) && !"POST".equals(position)) {
            throw new BusinessException(422, "加签类型无效");
        }
        List<WfNode> active = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
            .eq(WfNode::getInstanceId, instanceId)
            .eq(WfNode::getNodeKey, instance.getCurrentNode())
            .eq(WfNode::getStatus, "APPROVING"));
        WfNode node = active.stream()
            .filter(n -> operatorId.equals(n.getApproverId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(403, "无权对该审批加签"));
        if (targetUserId.equals(operatorId)) throw new BusinessException(422, "不能加签给自己");
        List<String> names = jdbcTemplate.queryForList(
            "SELECT real_name FROM `user` WHERE id = ? AND tenant_id = ? AND is_deleted = 0 AND status = 1",
            String.class, targetUserId, tenantId);
        if (names.isEmpty()) throw new BusinessException(422, "加签目标用户不存在或已停用");

        String newKey = "sign_" + System.currentTimeMillis();
        WfNode sign = new WfNode();
        sign.setTenantId(tenantId);
        sign.setInstanceId(instanceId);
        sign.setNodeKey(newKey);
        sign.setNodeName(("PRE".equals(position) ? "前加签-" : "后加签-") + names.get(0));
        sign.setApproverType("SPECIFIC_USER");
        sign.setTargetUserId(targetUserId);
        sign.setApproverId(targetUserId);
        sign.setApproverName(names.get(0));
        sign.setResolveMode("FIRST");
        sign.setTimeoutHours(node.getTimeoutHours());
        sign.setRejectPolicy(node.getRejectPolicy());
        sign.setStatus("WAITING");

        if ("POST".equals(position)) {
            // 排在当前节点之后:后续节点全部让位
            jdbcTemplate.update(
                "UPDATE wf_node SET node_order = node_order + 1 WHERE instance_id = ? AND node_order > ?",
                instanceId, node.getNodeOrder());
            sign.setNodeOrder(node.getNodeOrder() + 1);
            nodeMapper.insert(sign);
        } else {
            // 排在当前节点之前:当前组暂挂回 WAITING(保留已解析审批人),加签人先审
            jdbcTemplate.update(
                "UPDATE wf_node SET node_order = node_order + 1 WHERE instance_id = ? AND node_order >= ?",
                instanceId, node.getNodeOrder());
            sign.setNodeOrder(node.getNodeOrder());
            nodeMapper.insert(sign);
            jdbcTemplate.update(
                "UPDATE wf_node SET status = 'WAITING' WHERE instance_id = ? AND node_key = ? AND status = 'APPROVING'",
                instanceId, node.getNodeKey());
        }

        WfRecord record = new WfRecord();
        record.setTenantId(tenantId);
        record.setInstanceId(instanceId);
        record.setNodeId(node.getId());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setAction("PRE".equals(position) ? "ADDSIGN_PRE" : "ADDSIGN_POST");
        record.setTargetUserId(targetUserId);
        record.setBeforeStatus("PENDING");
        record.setAfterStatus("PENDING");
        recordMapper.insert(record);

        if ("PRE".equals(position)) {
            advance(instance, instance.getApplicantId(), false);
        } else {
            noticeService.send(tenantId, targetUserId,
                "APPROVAL_TODO", "你被加签为审批人",
                operatorName + " 在审批 " + instance.getInstanceNo() + " 中加签你为后置审批人",
                "approval", instanceId);
        }
    }

    /**
     * 推进到下一个待办节点；自动跳过 SELF_APPROVE，全部走完则实例通过。
     * strict=true(发起/重提):节点解析不到审批人直接报错拦截,便于及早修配置;
     * strict=false(审批中途):跳过并留痕,不能把审批人的操作打断。
     */
    private void advance(WfInstance instance, Long applicantId, boolean strict) {
        while (true) {
            WfNode next = nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>()
                .eq(WfNode::getInstanceId, instance.getId())
                .eq(WfNode::getStatus, "WAITING")
                .orderByAsc(WfNode::getNodeOrder)
                .last("LIMIT 1"));
            if (next == null) {
                instance.setStatus("APPROVED");
                instance.setCurrentNode(null);
                instanceMapper.updateById(instance);
                noticeService.send(instance.getTenantId(), instance.getApplicantId(),
                    "APPROVAL_RESULT", "审批已通过",
                    "你发起的审批 " + instance.getInstanceNo() + " 已全部通过",
                    "approval", instance.getId());
                return;
            }
            if (next.getApproverId() != null) {
                // 预定审批人节点(加签/前加签暂挂组恢复):直接激活整组,不重新解析
                List<WfNode> group = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
                    .eq(WfNode::getInstanceId, instance.getId())
                    .eq(WfNode::getNodeKey, next.getNodeKey())
                    .eq(WfNode::getStatus, "WAITING"));
                for (WfNode g : group) {
                    g.setStatus("APPROVING");
                    if (g.getTimeoutHours() != null && g.getDueAt() == null) {
                        g.setDueAt(LocalDateTime.now().plusHours(g.getTimeoutHours()));
                    }
                    nodeMapper.updateById(g);
                    noticeService.send(instance.getTenantId(), g.getApproverId(),
                        "APPROVAL_TODO", "有新的待办审批",
                        instance.getApplicantName() + " 提交的审批 " + instance.getInstanceNo() + " 待你处理",
                        "approval", instance.getId());
                }
                instance.setCurrentNode(next.getNodeKey());
                instanceMapper.updateById(instance);
                return;
            }
            List<ApprovalResolverService.Approver> approvers = resolverService.resolveAll(
                next.getApproverType(), next.getTargetUserId(), next.getTargetRoleId(), applicantId);
            if (approvers.isEmpty()) {
                if (!"SELF_APPROVE".equals(next.getApproverType())) {
                    String typeLabel = switch (next.getApproverType()) {
                        case "ROLE" -> "指定角色";
                        case "LEADER" -> "直属上级";
                        case "DEPT_LEADER" -> "部门负责人";
                        case "SPECIFIC_USER" -> "指定人员";
                        default -> next.getApproverType();
                    };
                    if (strict) {
                        throw new BusinessException(422,
                            "节点「" + next.getNodeName() + "」无可用审批人（" + typeLabel + "），请联系管理员检查配置");
                    }
                    next.setComment("无可用审批人（" + typeLabel + "），自动跳过");
                }
                next.setStatus("APPROVED");
                next.setResult("AUTO");
                next.setActionAt(LocalDateTime.now());
                nodeMapper.updateById(next);
                continue;
            }
            String mode = next.getResolveMode();
            boolean multi = ("ALL".equals(mode) || "ANYONE".equals(mode)) && approvers.size() > 1;
            next.setApproverId(approvers.get(0).id());
            next.setApproverName(approvers.get(0).name());
            next.setStatus("APPROVING");
            if (next.getTimeoutHours() != null) {
                next.setDueAt(LocalDateTime.now().plusHours(next.getTimeoutHours()));
            }
            nodeMapper.updateById(next);
            if (multi) {
                // 会签/或签：每位审批人一行,同 node_key 分组
                for (int i = 1; i < approvers.size(); i++) {
                    WfNode sibling = new WfNode();
                    sibling.setTenantId(next.getTenantId());
                    sibling.setInstanceId(next.getInstanceId());
                    sibling.setNodeKey(next.getNodeKey());
                    sibling.setNodeName(next.getNodeName());
                    sibling.setApproverType(next.getApproverType());
                    sibling.setTargetUserId(next.getTargetUserId());
                    sibling.setTargetRoleId(next.getTargetRoleId());
                    sibling.setResolveMode(mode);
                    sibling.setNodeOrder(next.getNodeOrder());
                    sibling.setApproverId(approvers.get(i).id());
                    sibling.setApproverName(approvers.get(i).name());
                    sibling.setStatus("APPROVING");
                    sibling.setTimeoutHours(next.getTimeoutHours());
                    sibling.setDueAt(next.getDueAt());
                    nodeMapper.insert(sibling);
                }
            }
            instance.setCurrentNode(next.getNodeKey());
            instanceMapper.updateById(instance);
            for (ApprovalResolverService.Approver a : (multi ? approvers : approvers.subList(0, 1))) {
                noticeService.send(instance.getTenantId(), a.id(),
                    "APPROVAL_TODO", "有新的待办审批",
                    instance.getApplicantName() + " 提交的审批 " + instance.getInstanceNo() + " 待你处理",
                    "approval", instance.getId());
            }
            return;
        }
    }

    public ApprovalInstanceVO detail(Long instanceId) {
        Long tenantId = TenantContext.getTenantId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null || !instance.getTenantId().equals(tenantId)) {
            throw new BusinessException(404, "审批实例不存在");
        }
        List<WfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
            .eq(WfNode::getInstanceId, instanceId)
            .orderByAsc(WfNode::getNodeOrder));

        // 数据权限:申请人 / 已到达节点的审批人(WAITING=未到达,不可提前窥看) / 持 manage 权限
        Long me = TenantContext.getUserId();
        boolean related = instance.getApplicantId().equals(me)
            || nodes.stream().anyMatch(n -> !"WAITING".equals(n.getStatus())
                && (me.equals(n.getApproverId()) || me.equals(n.getActionBy())));
        if (!related && !hasManageAuthority()) {
            throw new BusinessException(403, "无权查看该审批");
        }

        ApprovalInstanceVO vo = toVO(instance, nodes);

        // 填充表单字段定义(供前端结构化展示表单数据)
        WfDefinition definition = definitionMapper.selectById(instance.getDefinitionId());
        if (definition != null) {
            vo.setFieldsJson(definition.getFieldsJson());
            vo.setFormName(definition.getFormName());
        }

        // 检测当前节点审批人是否缺失(warning 提示)
        if ("PENDING".equals(instance.getStatus()) && instance.getCurrentNode() != null) {
            List<WfNode> active = nodes.stream()
                .filter(n -> instance.getCurrentNode().equals(n.getNodeKey())
                    && "APPROVING".equals(n.getStatus()))
                .toList();
            if (!active.isEmpty() && active.stream().allMatch(n -> n.getApproverId() == null)) {
                vo.setWarning("当前节点「" + active.get(0).getNodeName() + "」无可用审批人，请联系管理员");
            }
        }

        return vo;
    }

    private boolean hasManageAuthority() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .anyMatch(a -> "PERM_*".equals(a)
                || "PERM_approval:manage".equals(a)
                || "PERM_platform:approval:manage".equals(a));
    }

    public List<ApprovalInstanceVO> myTodo(Long userId) {
        Long tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT instance_id, due_at FROM wf_node WHERE tenant_id = ? AND approver_id = ? AND status = 'APPROVING'",
            tenantId, userId);
        if (rows.isEmpty()) return List.of();
        Map<Long, LocalDateTime> dueMap = new java.util.HashMap<>();
        List<Long> instanceIds = new java.util.ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long iid = ((Number) r.get("instance_id")).longValue();
            instanceIds.add(iid);
            Object due = r.get("due_at");
            if (due instanceof java.sql.Timestamp ts) dueMap.put(iid, ts.toLocalDateTime());
            else if (due instanceof LocalDateTime ldt) dueMap.put(iid, ldt);
        }
        List<ApprovalInstanceVO> vos = listByIds(instanceIds);
        vos.forEach(v -> v.setMyDueAt(dueMap.get(v.getId())));
        return vos;
    }

    public List<ApprovalInstanceVO> myApplied(Long userId) {
        List<WfInstance> instances = instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
            .eq(WfInstance::getApplicantId, userId)
            .orderByDesc(WfInstance::getId));
        return instances.stream().map(i -> toVO(i, List.of())).toList();
    }

    public List<ApprovalInstanceVO> myDone(Long userId) {
        Long tenantId = TenantContext.getTenantId();
        List<Long> instanceIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT instance_id FROM wf_node WHERE tenant_id = ? AND approver_id = ? " +
            "AND status IN ('APPROVED','REJECTED') AND action_by IS NOT NULL",
            Long.class, tenantId, userId);
        return listByIds(instanceIds);
    }

    private List<ApprovalInstanceVO> listByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        List<WfInstance> instances = instanceMapper.selectBatchIds(ids);
        return instances.stream()
            .sorted((a, b) -> b.getId().compareTo(a.getId()))
            .map(i -> toVO(i, List.of()))
            .toList();
    }

    /** 撤回：申请人撤回自己的审批中实例,全部未完成节点作废 */
    @Transactional
    public void cancel(Long instanceId, Long operatorId, String operatorName) {
        Long tenantId = TenantContext.getTenantId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null || !instance.getTenantId().equals(tenantId)) {
            throw new BusinessException(404, "审批实例不存在");
        }
        if (!instance.getApplicantId().equals(operatorId)) {
            throw new BusinessException(403, "只能撤回自己发起的审批");
        }
        if (!"PENDING".equals(instance.getStatus()) && !"RETURNED".equals(instance.getStatus())) {
            throw new BusinessException(409, "该审批已结束,无法撤回");
        }
        String before = instance.getStatus();
        instance.setStatus("CANCELED");
        instance.setCurrentNode(null);
        instanceMapper.updateById(instance);
        jdbcTemplate.update(
            "UPDATE wf_node SET status = 'CANCELED' WHERE instance_id = ? AND status IN ('WAITING','APPROVING')",
            instanceId);
        writeRecord(instanceId, null, operatorId, operatorName, "CANCEL", null, before, "CANCELED");
    }

    /** 转交：当前审批人把自己的待办节点移交给同租户其他用户 */
    @Transactional
    public void transfer(Long instanceId, Long targetUserId, Long operatorId, String operatorName) {
        Long tenantId = TenantContext.getTenantId();
        WfInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null || !instance.getTenantId().equals(tenantId)) {
            throw new BusinessException(404, "审批实例不存在");
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new BusinessException(409, "该审批已结束，无法转交");
        }
        List<WfNode> active = nodeMapper.selectList(new LambdaQueryWrapper<WfNode>()
            .eq(WfNode::getInstanceId, instanceId)
            .eq(WfNode::getNodeKey, instance.getCurrentNode())
            .eq(WfNode::getStatus, "APPROVING"));
        WfNode node = active.stream()
            .filter(n -> operatorId.equals(n.getApproverId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(403, "无权转交该审批"));
        if (active.stream().anyMatch(n -> targetUserId.equals(n.getApproverId()))) {
            throw new BusinessException(409, "目标用户已是该节点审批人");
        }
        List<String> names = jdbcTemplate.queryForList(
            "SELECT real_name FROM `user` WHERE id = ? AND tenant_id = ? AND is_deleted = 0 AND status = 1",
            String.class, targetUserId, tenantId);
        if (names.isEmpty()) throw new BusinessException(422, "转交目标用户不存在或已停用");

        node.setApproverId(targetUserId);
        node.setApproverName(names.get(0));
        nodeMapper.updateById(node);

        WfRecord record = new WfRecord();
        record.setTenantId(tenantId);
        record.setInstanceId(instanceId);
        record.setNodeId(node.getId());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setAction("TRANSFER");
        record.setTargetUserId(targetUserId);
        record.setBeforeStatus("PENDING");
        record.setAfterStatus("PENDING");
        recordMapper.insert(record);

        noticeService.send(tenantId, targetUserId,
            "APPROVAL_TODO", "有转交给你的审批",
            operatorName + " 将审批 " + instance.getInstanceNo() + " 转交给你处理",
            "approval", instanceId);
    }

    /** 我的代理规则(一人一条),无则 null */
    public WfDelegate delegateGet(Long userId) {
        return delegateMapper.selectOne(new LambdaQueryWrapper<WfDelegate>()
            .eq(WfDelegate::getTenantId, TenantContext.getTenantId())
            .eq(WfDelegate::getOwnerId, userId));
    }

    /** 设置/更新代理规则(upsert) */
    @Transactional
    public void delegateSave(Long ownerId, String ownerName, Long delegateId,
                             LocalDateTime startAt, LocalDateTime endAt, Integer status) {
        Long tenantId = TenantContext.getTenantId();
        if (delegateId.equals(ownerId)) throw new BusinessException(422, "不能委托给自己");
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BusinessException(422, "代理起止时间无效");
        }
        List<String> names = jdbcTemplate.queryForList(
            "SELECT real_name FROM `user` WHERE id = ? AND tenant_id = ? AND is_deleted = 0 AND status = 1",
            String.class, delegateId, tenantId);
        if (names.isEmpty()) throw new BusinessException(422, "代理人不存在或已停用");

        WfDelegate existing = delegateGet(ownerId);
        WfDelegate d = existing != null ? existing : new WfDelegate();
        d.setTenantId(tenantId);
        d.setOwnerId(ownerId);
        d.setOwnerName(ownerName);
        d.setDelegateId(delegateId);
        d.setDelegateName(names.get(0));
        d.setStartAt(startAt);
        d.setEndAt(endAt);
        d.setStatus(status != null ? status : 1);
        if (existing != null) delegateMapper.updateById(d);
        else delegateMapper.insert(d);
    }

    /** 统计：租户维度汇总(状态分布/驳回率/平均时长/表单分布)。ponytail: 内存聚合,量大再下推 SQL */
    public Map<String, Object> statistics() {
        List<WfInstance> all = instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
            .eq(WfInstance::getTenantId, TenantContext.getTenantId()));
        long total = all.size();
        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        Map<String, Long> byFormType = new java.util.LinkedHashMap<>();
        long finished = 0, rejected = 0, durationMinutesSum = 0, durationCount = 0;
        for (WfInstance i : all) {
            byStatus.merge(i.getStatus(), 1L, Long::sum);
            byFormType.merge(i.getFormType(), 1L, Long::sum);
            if ("APPROVED".equals(i.getStatus()) || "REJECTED".equals(i.getStatus())) {
                finished++;
                if ("REJECTED".equals(i.getStatus())) rejected++;
                if (i.getCreatedAt() != null && i.getUpdatedAt() != null) {
                    durationMinutesSum += java.time.Duration.between(i.getCreatedAt(), i.getUpdatedAt()).toMinutes();
                    durationCount++;
                }
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", total);
        result.put("byStatus", byStatus);
        result.put("byFormType", byFormType);
        result.put("rejectionRate", finished == 0 ? 0 : Math.round(rejected * 1000.0 / finished) / 10.0);
        result.put("avgDurationMinutes", durationCount == 0 ? 0 : durationMinutesSum / durationCount);
        return result;
    }

    private ApprovalInstanceVO toVO(WfInstance i, List<WfNode> nodes) {
        return ApprovalInstanceVO.builder()
            .id(i.getId()).instanceNo(i.getInstanceNo()).formType(i.getFormType())
            .formData(i.getFormData()).applicantId(i.getApplicantId()).applicantName(i.getApplicantName())
            .status(i.getStatus()).currentNode(i.getCurrentNode()).priority(i.getPriority())
            .createdAt(i.getCreatedAt())
            .nodes(nodes.stream().map(n -> ApprovalInstanceVO.NodeVO.builder()
                .id(n.getId()).nodeKey(n.getNodeKey()).nodeName(n.getNodeName())
                .approverId(n.getApproverId()).approverName(n.getApproverName())
                .status(n.getStatus()).result(n.getResult())
                .comment(n.getComment()).actionAt(n.getActionAt()).dueAt(n.getDueAt())
                .rejectPolicy(n.getRejectPolicy()).build())
                .toList())
            .build();
    }

    private void writeRecord(Long instanceId, Long nodeId, Long operatorId, String operatorName,
                              String action, String comment, String before, String after) {
        WfRecord record = new WfRecord();
        record.setTenantId(TenantContext.getTenantId());
        record.setInstanceId(instanceId);
        record.setNodeId(nodeId);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setAction(action);
        record.setComment(comment);
        record.setBeforeStatus(before);
        record.setAfterStatus(after);
        recordMapper.insert(record);
    }

    private List<ApprovalNodeConfig> parseNodes(String configJson) {
        try {
            return objectMapper.readValue(configJson, ApprovalNodeConfig.Wrapper.class).getNodes();
        } catch (Exception e) {
            throw new BusinessException(500, "审批流程配置解析失败：" + e.getMessage());
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException(422, "表单数据格式错误");
        }
    }

    private boolean evalCondition(String condition, Map<String, Object> formData) {
        if (condition == null || condition.isBlank()) return true;
        Matcher m = CONDITION_PATTERN.matcher(condition.trim());
        if (!m.matches()) return true;
        Object lhsObj = formData == null ? null : formData.get(m.group(1));
        if (lhsObj == null) return false;
        try {
            double lhs = Double.parseDouble(lhsObj.toString());
            double rhs = Double.parseDouble(m.group(3).trim());
            return switch (m.group(2)) {
                case ">" -> lhs > rhs;
                case ">=" -> lhs >= rhs;
                case "<" -> lhs < rhs;
                case "<=" -> lhs <= rhs;
                case "==" -> lhs == rhs;
                case "!=" -> lhs != rhs;
                default -> true;
            };
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String genInstanceNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "WF" + date + ThreadLocalRandom.current().nextInt(100000, 999999);
    }
}
