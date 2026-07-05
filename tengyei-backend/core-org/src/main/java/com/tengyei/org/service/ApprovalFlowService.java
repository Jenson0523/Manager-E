package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.ApprovalFlowSaveDTO;
import com.tengyei.org.dto.ApprovalFlowVO;
import com.tengyei.org.dto.ApprovalNodeConfig;
import com.tengyei.org.entity.WfDefinition;
import com.tengyei.org.mapper.WfDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 审批流程配置管理：Phase 1 每个 form_type 仅维护一条定义，upsert 即更新+版本号自增 */
@Service
@RequiredArgsConstructor
public class ApprovalFlowService {

    private final WfDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ApprovalFlowVO> list() {
        List<WfDefinition> defs = definitionMapper.selectList(new LambdaQueryWrapper<WfDefinition>()
            .eq(WfDefinition::getTenantId, TenantContext.getTenantId())
            .orderByDesc(WfDefinition::getId));
        return defs.stream().map(this::toVO).toList();
    }

    public Long save(ApprovalFlowSaveDTO dto) {
        validateConfigJson(dto.getConfigJson());
        Long tenantId = TenantContext.getTenantId();
        WfDefinition existing = definitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
            .eq(WfDefinition::getTenantId, tenantId)
            .eq(WfDefinition::getFormType, dto.getFormType()));
        if (existing != null) {
            existing.setFormName(dto.getFormName());
            existing.setProcessKey(dto.getProcessKey());
            existing.setConfigJson(dto.getConfigJson());
            existing.setFieldsJson(dto.getFieldsJson());
            existing.setVersion(existing.getVersion() + 1);
            definitionMapper.updateById(existing);
            return existing.getId();
        }
        WfDefinition d = new WfDefinition();
        d.setTenantId(tenantId);
        d.setFormType(dto.getFormType());
        d.setFormName(dto.getFormName());
        d.setProcessKey(dto.getProcessKey());
        d.setConfigJson(dto.getConfigJson());
        d.setFieldsJson(dto.getFieldsJson());
        d.setVersion(1);
        d.setStatus(1);
        d.setIsDefault(1);
        definitionMapper.insert(d);
        return d.getId();
    }

    public void toggleStatus(Long id, Integer status) {
        WfDefinition d = definitionMapper.selectById(id);
        if (d == null || !d.getTenantId().equals(TenantContext.getTenantId())) {
            throw new BusinessException(404, "流程定义不存在");
        }
        d.setStatus(status);
        definitionMapper.updateById(d);
    }

    private void validateConfigJson(String json) {
        try {
            ApprovalNodeConfig.Wrapper wrapper = objectMapper.readValue(json, ApprovalNodeConfig.Wrapper.class);
            if (wrapper.getNodes() == null || wrapper.getNodes().isEmpty()) {
                throw new BusinessException(422, "审批节点配置至少需要一个节点");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(422, "审批节点配置 JSON 格式错误：" + e.getMessage());
        }
    }

    /** 发起人可选的启用表单(含字段定义,供动态渲染) */
    public List<ApprovalFlowVO> enabledForms() {
        List<WfDefinition> defs = definitionMapper.selectList(new LambdaQueryWrapper<WfDefinition>()
            .eq(WfDefinition::getTenantId, TenantContext.getTenantId())
            .eq(WfDefinition::getStatus, 1)
            .orderByAsc(WfDefinition::getId));
        return defs.stream().map(this::toVO).toList();
    }

    private ApprovalFlowVO toVO(WfDefinition d) {
        return ApprovalFlowVO.builder()
            .id(d.getId()).formType(d.getFormType()).formName(d.getFormName())
            .processKey(d.getProcessKey()).configJson(d.getConfigJson())
            .fieldsJson(d.getFieldsJson())
            .version(d.getVersion()).status(d.getStatus()).build();
    }
}
