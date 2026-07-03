package com.tengyei.org.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.ApprovalFlowSaveDTO;
import com.tengyei.org.dto.ApprovalFlowVO;
import com.tengyei.org.service.ApprovalFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/approval/flows")
@RequiredArgsConstructor
public class ApprovalFlowController {

    private final ApprovalFlowService flowService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_approval:manage')")
    public Result<List<ApprovalFlowVO>> list() {
        return Result.ok(flowService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_approval:manage')")
    @Auditable(module = "审批流程", actionType = "CREATE", description = "保存审批流程配置")
    public Result<Map<String, Long>> save(@Valid @RequestBody ApprovalFlowSaveDTO dto) {
        return Result.ok(Map.of("id", flowService.save(dto)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_approval:manage')")
    @Auditable(module = "审批流程", actionType = "UPDATE", description = "变更审批流程状态")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        flowService.toggleStatus(id, body.get("status"));
        return Result.ok();
    }
}
