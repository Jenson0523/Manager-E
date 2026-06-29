package com.tengyei.org.controller;

import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.UserCreateDTO;
import com.tengyei.org.dto.UserUpdateDTO;
import com.tengyei.org.dto.UserVO;
import com.tengyei.org.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_user:view')")
    public Result<PageResult<UserVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long roleId) {
        return Result.ok(userService.page(page, size, keyword, deptId, roleId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_user:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.ok(Map.of("id", userService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_user:reset_pwd')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return Result.ok();
    }
}
