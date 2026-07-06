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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_platform:approval:view')")
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

    @PutMapping("/{id}/transfer")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:transfer','PERM_platform:approval:transfer')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "审批转交")
    public Result<Void> transfer(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        engineService.transfer(id, body.get("targetUserId"),
            TenantContext.getUserId(), TenantContext.getUserName());
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
