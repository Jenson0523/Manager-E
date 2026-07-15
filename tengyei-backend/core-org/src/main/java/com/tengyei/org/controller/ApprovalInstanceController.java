package com.tengyei.org.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.ApprovalApplyDTO;
import com.tengyei.org.dto.ApprovalApproveDTO;
import com.tengyei.org.dto.ApprovalInstanceVO;
import com.tengyei.org.service.ApprovalEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/approval/instances")
@RequiredArgsConstructor
public class ApprovalInstanceController {

    private final ApprovalEngineService engineService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:apply','PERM_platform:approval:apply')")
    @Auditable(module = "审批", actionType = "CREATE", description = "发起审批")
    public Result<Map<String, Long>> apply(@Valid @RequestBody ApprovalApplyDTO dto) {
        Long id = engineService.apply(dto, TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok(Map.of("id", id));
    }

    // 详情放行任一审批权限:只有发起权限的人也要能看自己发起的单。
    // 数据权限由 service 层校验(发起人/已到达节点审批人/被抄送人/manage),不会越权看别人的单
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_approval:apply','PERM_approval:approve','PERM_approval:transfer','PERM_approval:manage'," +
            "'PERM_platform:approval:view','PERM_platform:approval:apply','PERM_platform:approval:approve','PERM_platform:approval:transfer','PERM_platform:approval:manage')")
    public Result<ApprovalInstanceVO> detail(@PathVariable Long id) {
        return Result.ok(engineService.detail(id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:cancel','PERM_platform:approval:cancel')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "撤回审批")
    public Result<Void> cancel(@PathVariable Long id) {
        engineService.cancel(id, TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok();
    }

    /** fromUserId 可选:manage 权限者代为转交多审批人节点时指明原审批人 */
    @PutMapping("/{id}/transfer")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:transfer','PERM_approval:manage','PERM_platform:approval:transfer','PERM_platform:approval:manage')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "审批转交")
    public Result<Void> transfer(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        engineService.transfer(id, body.get("targetUserId"), body.get("fromUserId"),
            TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok();
    }

    /** 被退回后重新提交(可携带修改后的表单数据) */
    @PutMapping("/{id}/resubmit")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:apply','PERM_platform:approval:apply')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "重新提交审批")
    public Result<Void> resubmit(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = body != null ? (Map<String, Object>) body.get("formData") : null;
        engineService.resubmit(id, formData, TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok();
    }

    /** 加签:PRE=前加签(其先审) / POST=后加签(己审后其再审) */
    @PutMapping("/{id}/addsign")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:approve','PERM_platform:approval:approve')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "审批加签")
    public Result<Void> addSign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long targetUserId = body.get("targetUserId") != null ? ((Number) body.get("targetUserId")).longValue() : null;
        String position = (String) body.get("position");
        if (targetUserId == null) throw new com.tengyei.common.exception.BusinessException(422, "请选择加签人");
        engineService.addSign(id, targetUserId, position, TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok();
    }

    @PutMapping("/{id}/act")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:approve','PERM_approval:reject','PERM_platform:approval:approve','PERM_platform:approval:reject')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "审批处理")
    public Result<Void> act(@PathVariable Long id, @Valid @RequestBody ApprovalApproveDTO dto) {
        engineService.act(id, dto.getAction(), dto.getComment(),
            TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok();
    }
}
