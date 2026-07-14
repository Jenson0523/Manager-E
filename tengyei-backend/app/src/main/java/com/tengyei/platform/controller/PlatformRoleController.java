package com.tengyei.platform.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import com.tengyei.platform.dto.PlatformRoleDTO;
import com.tengyei.platform.dto.PlatformRoleVO;
import com.tengyei.platform.service.PlatformRbacService;
import com.tengyei.rbac.dto.PermissionGroupVO;
import com.tengyei.rbac.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/roles")
@RequiredArgsConstructor
public class PlatformRoleController {

    private final PlatformRbacService service;
    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:view')")
    public Result<List<PlatformRoleVO>> list() {
        return Result.ok(service.listRoles());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:view')")
    public Result<List<PermissionGroupVO>> permissions() {
        return Result.ok(permissionService.grouped("platform"));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:create')")
    @Auditable(module = "平台角色", actionType = "CREATE", description = "新建平台角色")
    public Result<Map<String, Long>> create(@Valid @RequestBody PlatformRoleDTO dto) {
        return Result.ok(Map.of("id", service.createRole(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:edit')")
    @Auditable(module = "平台角色", actionType = "UPDATE", description = "编辑平台角色")
    public Result<Void> update(@PathVariable(name="id") Long id, @Valid @RequestBody PlatformRoleDTO dto) {
        service.updateRole(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:delete')")
    @Auditable(module = "平台角色", actionType = "DELETE", description = "删除平台角色")
    public Result<Void> delete(@PathVariable(name="id") Long id) {
        service.deleteRole(id);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:view')")
    public Result<List<Long>> rolePermissions(@PathVariable(name="id") Long id) {
        return Result.ok(service.rolePermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:edit')")
    @Auditable(module = "平台角色", actionType = "UPDATE", description = "配置平台角色权限")
    public Result<Void> assignPermissions(@PathVariable(name="id") Long id, @RequestBody Map<String, List<Long>> body) {
        service.assignRolePermissions(id, body.get("permissionIds"));
        return Result.ok();
    }
}
