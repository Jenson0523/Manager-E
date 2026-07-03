package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.ApprovalApplyDTO;
import com.tengyei.org.dto.ApprovalInstanceVO;
import com.tengyei.org.dto.ApprovalNodeConfig;
import com.tengyei.org.entity.WfDefinition;
import com.tengyei.org.entity.WfInstance;
import com.tengyei.org.entity.WfNode;
import com.tengyei.org.entity.WfRecord;
import com.tengyei.org.mapper.WfDefinitionMapper;
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
    private final ApprovalResolverService resolverService;
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
            node.setResolveMode(cfg.getResolveMode());
            node.setNodeOrder(order++);
            node.setStatus("WAITING");
            nodeMapper.insert(node);
        }

        writeRecord(instance.getId(), null, applicantId, applicantName, "APPLY", null, null, "PENDING");
        advance(instance, applicantId);
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

        WfNode node = nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>()
            .eq(WfNode::getInstanceId, instanceId)
            .eq(WfNode::getNodeKey, instance.getCurrentNode())
            .eq(WfNode::getStatus, "APPROVING"));
        if (node == null) throw new BusinessException(409, "当前节点状态异常，请刷新重试");
        if (!node.getApproverId().equals(operatorId)) {
            throw new BusinessException(403, "无权处理该审批");
        }

        String before = instance.getStatus();
        node.setResult(action);
        node.setComment(comment);
        node.setActionBy(operatorId);
        node.setActionAt(LocalDateTime.now());
        node.setStatus("REJECT".equals(action) ? "REJECTED" : "APPROVED");
        nodeMapper.updateById(node);

        if ("REJECT".equals(action)) {
            instance.setStatus("REJECTED");
            instance.setCurrentNode(null);
            instanceMapper.updateById(instance);
            jdbcTemplate.update(
                "UPDATE wf_node SET status = 'CANCELED' WHERE instance_id = ? AND status = 'WAITING'",
                instanceId);
        } else {
            advance(instance, instance.getApplicantId());
        }
        writeRecord(instanceId, node.getId(), operatorId, operatorName, action, comment, before, instance.getStatus());
    }

    /** 推进到下一个待办节点；自动跳过 SELF_APPROVE，全部走完则实例通过 */
    private void advance(WfInstance instance, Long applicantId) {
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
                return;
            }
            ApprovalResolverService.Approver approver = resolverService.resolve(
                next.getApproverType(), next.getTargetUserId(), next.getTargetRoleId(), applicantId);
            if (approver == null) {
                next.setStatus("APPROVED");
                next.setResult("AUTO");
                next.setActionAt(LocalDateTime.now());
                nodeMapper.updateById(next);
                continue;
            }
            next.setApproverId(approver.id());
            next.setApproverName(approver.name());
            next.setStatus("APPROVING");
            nodeMapper.updateById(next);
            instance.setCurrentNode(next.getNodeKey());
            instanceMapper.updateById(instance);
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
        return toVO(instance, nodes);
    }

    public List<ApprovalInstanceVO> myTodo(Long userId) {
        Long tenantId = TenantContext.getTenantId();
        List<Long> instanceIds = jdbcTemplate.queryForList(
            "SELECT instance_id FROM wf_node WHERE tenant_id = ? AND approver_id = ? AND status = 'APPROVING'",
            Long.class, tenantId, userId);
        return listByIds(instanceIds);
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

    private ApprovalInstanceVO toVO(WfInstance i, List<WfNode> nodes) {
        return ApprovalInstanceVO.builder()
            .id(i.getId()).instanceNo(i.getInstanceNo()).formType(i.getFormType())
            .formData(i.getFormData()).applicantId(i.getApplicantId()).applicantName(i.getApplicantName())
            .status(i.getStatus()).currentNode(i.getCurrentNode()).priority(i.getPriority())
            .createdAt(i.getCreatedAt())
            .nodes(nodes.stream().map(n -> ApprovalInstanceVO.NodeVO.builder()
                .id(n.getId()).nodeKey(n.getNodeKey()).nodeName(n.getNodeName())
                .approverName(n.getApproverName()).status(n.getStatus()).result(n.getResult())
                .comment(n.getComment()).actionAt(n.getActionAt()).build())
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
