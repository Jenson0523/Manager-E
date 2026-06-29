package com.tengyei.controller;

import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        Long userId = TenantContext.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT password FROM `user` WHERE id = ? AND is_deleted = 0", userId);
        if (rows.isEmpty()) throw new BusinessException(404, "用户不存在");

        String encoded = (String) rows.get(0).get("password");
        if (!passwordEncoder.matches(dto.getOldPassword(), encoded)) {
            throw new BusinessException(400, "原密码错误");
        }

        jdbcTemplate.update(
            "UPDATE `user` SET password = ?, pwd_reset_required = 0, login_fail_count = 0 WHERE id = ?",
            passwordEncoder.encode(dto.getNewPassword()), userId);
        return Result.ok();
    }

    @Data
    public static class ChangePasswordDTO {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "新密码至少 6 位")
        private String newPassword;
    }
}
