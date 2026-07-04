package com.tengyei.org.controller;

import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.ApprovalInstanceVO;
import com.tengyei.org.service.ApprovalEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class ApprovalTodoController {

    private final ApprovalEngineService engineService;

    @GetMapping("/todo")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_platform:approval:view')")
    public Result<List<ApprovalInstanceVO>> todo() {
        return Result.ok(engineService.myTodo(TenantContext.getUserId()));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:apply','PERM_platform:approval:apply')")
    public Result<List<ApprovalInstanceVO>> my() {
        return Result.ok(engineService.myApplied(TenantContext.getUserId()));
    }

    @GetMapping("/done")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_platform:approval:view')")
    public Result<List<ApprovalInstanceVO>> done() {
        return Result.ok(engineService.myDone(TenantContext.getUserId()));
    }
}
