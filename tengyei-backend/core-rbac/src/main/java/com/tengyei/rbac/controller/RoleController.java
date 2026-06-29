package com.tengyei.rbac.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import com.tengyei.rbac.dto.RoleSaveDTO;
import com.tengyei.rbac.dto.RoleVO;
import com.tengyei.rbac.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_role:view')")
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_role:create')")
    @Auditable(module = "角色管理", actionType = "CREATE", description = "新建角色")
    public Result<Map<String, Long>> create(@Valid @RequestBody RoleSaveDTO dto) {
        return Result.ok(Map.of("id", roleService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_role:edit')")
    @Auditable(module = "角色管理", actionType = "UPDATE", description = "编辑角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        roleService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_role:delete')")
    @Auditable(module = "角色管理", actionType = "DELETE", description = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERM_role:view')")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.ok(roleService.permissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERM_role:edit')")
    @Auditable(module = "角色管理", actionType = "UPDATE", description = "配置角色权限")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(id, body.get("permissionIds"));
        return Result.ok();
    }
}
