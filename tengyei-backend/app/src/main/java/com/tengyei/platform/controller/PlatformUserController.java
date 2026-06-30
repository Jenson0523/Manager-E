package com.tengyei.platform.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import com.tengyei.platform.dto.PlatformUserDTO;
import com.tengyei.platform.dto.PlatformUserVO;
import com.tengyei.platform.service.PlatformRbacService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/users")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformRbacService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:view')")
    public Result<List<PlatformUserVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.listUsers(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:create')")
    @Auditable(module = "平台账号", actionType = "CREATE", description = "新建平台账号")
    public Result<Map<String, Long>> create(@Valid @RequestBody PlatformUserDTO dto) {
        return Result.ok(Map.of("id", service.createUser(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:edit')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "编辑平台账号")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlatformUserDTO dto) {
        service.updateUser(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:edit')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "变更平台账号状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.changeUserStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:edit')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "分配平台账号角色")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        service.assignUserRoles(id, body.get("roleIds"));
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:reset_pwd')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "重置平台账号密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.resetUserPassword(id, body.get("password"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:delete')")
    @Auditable(module = "平台账号", actionType = "DELETE", description = "删除平台账号")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteUser(id);
        return Result.ok();
    }
}
