package com.tengyei.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_setting:view')")
    public Result<List<Map<String, Object>>> list() {
        Long tenantId = TenantContext.isSuperAdmin() ? 0L : TenantContext.getTenantId();
        return Result.ok(jdbcTemplate.queryForList(
            "SELECT id, config_key AS configKey, config_value AS configValue, description " +
            "FROM system_config WHERE tenant_id = ? ORDER BY config_key",
            tenantId));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_setting:edit')")
    @Auditable(module = "系统配置", actionType = "UPDATE", description = "修改系统配置")
    public Result<Void> update(@PathVariable(name="key") String key, @Valid @RequestBody ConfigValueDTO dto) {
        Long tenantId = TenantContext.isSuperAdmin() ? 0L : TenantContext.getTenantId();
        int affected = jdbcTemplate.update(
            "UPDATE system_config SET config_value = ? WHERE tenant_id = ? AND config_key = ?",
            dto.getValue(), tenantId, key);
        if (affected == 0) {
            jdbcTemplate.update(
                "INSERT INTO system_config (tenant_id, config_key, config_value) VALUES (?, ?, ?)",
                tenantId, key, dto.getValue());
        }
        return Result.ok();
    }

    @Data
    public static class ConfigValueDTO {
        @NotNull(message = "配置值不能为空")
        private String value;
    }
}
