package com.tengyei.org.controller;

import com.alibaba.excel.EasyExcel;
import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.UserCreateDTO;
import com.tengyei.org.dto.UserExportVO;
import com.tengyei.org.dto.UserUpdateDTO;
import com.tengyei.org.dto.UserVO;
import com.tengyei.org.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_user:view')")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            HttpServletResponse response) throws Exception {
        List<UserExportVO> data = userService.export(keyword, deptId);
        String fileName = URLEncoder.encode("人员列表_" + LocalDate.now(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        EasyExcel.write(response.getOutputStream(), UserExportVO.class)
            .sheet("人员列表")
            .doWrite(data);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_user:create')")
    @Auditable(module = "人员管理", actionType = "CREATE", description = "新建人员")
    public Result<Map<String, Long>> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.ok(Map.of("id", userService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    @Auditable(module = "人员管理", actionType = "UPDATE", description = "编辑人员信息")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    @Auditable(module = "人员管理", actionType = "UPDATE", description = "变更人员状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    @Auditable(module = "人员管理", actionType = "UPDATE", description = "分配人员角色")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_user:reset_pwd')")
    @Auditable(module = "人员管理", actionType = "UPDATE", description = "重置人员密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return Result.ok();
    }
}
