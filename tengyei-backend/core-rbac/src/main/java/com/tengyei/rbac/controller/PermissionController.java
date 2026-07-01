package com.tengyei.rbac.controller;

import com.tengyei.common.response.Result;
import com.tengyei.rbac.dto.PermissionGroupVO;
import com.tengyei.rbac.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*', 'PERM_role:view', 'PERM_platform:role:view')")
    public Result<List<PermissionGroupVO>> grouped() {
        return Result.ok(permissionService.grouped());
    }
}
