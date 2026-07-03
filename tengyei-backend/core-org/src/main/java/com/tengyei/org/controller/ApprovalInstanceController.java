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
    @PreAuthorize("hasAuthority('PERM_approval:apply')")
    @Auditable(module = "审批", actionType = "CREATE", description = "发起审批")
    public Result<Map<String, Long>> apply(@Valid @RequestBody ApprovalApplyDTO dto) {
        Long id = engineService.apply(dto, TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok(Map.of("id", id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_approval:view')")
    public Result<ApprovalInstanceVO> detail(@PathVariable Long id) {
        return Result.ok(engineService.detail(id));
    }

    @PutMapping("/{id}/act")
    @PreAuthorize("hasAnyAuthority('PERM_approval:approve','PERM_approval:reject')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "审批处理")
    public Result<Void> act(@PathVariable Long id, @Valid @RequestBody ApprovalApproveDTO dto) {
        engineService.act(id, dto.getAction(), dto.getComment(),
            TenantContext.getUserId(), TenantContext.getUserName());
        return Result.ok();
    }
}
