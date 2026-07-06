package com.tengyei.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
public class ModuleRegistryController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_platform:module:view')")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) Integer status) {

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (module_code LIKE ? OR module_name LIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
        }
        if (status != null) {
            where.append(" AND status = ?");
            params.add(status);
        }

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "SELECT id, module_code AS moduleCode, module_name AS moduleName, " +
            "version, entry_url AS entryUrl, menu_config AS menuConfig, " +
            "permissions, status, created_at AS createdAt, updated_at AS updatedAt " +
            "FROM module_registry " + where + " ORDER BY id",
            params.toArray()
        );

        return Result.ok(records);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_platform:module:create')")
    @Auditable(module = "模块管理", actionType = "CREATE", description = "注册新模块")
    public Result<Void> create(@Valid @RequestBody ModuleDTO dto) {
        // Check uniqueness
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM module_registry WHERE module_code = ?",
            Integer.class, dto.getModuleCode());
        if (count != null && count > 0) {
            return Result.fail("模块编码已存在");
        }

        jdbcTemplate.update(
            "INSERT INTO module_registry (module_code, module_name, version, entry_url, " +
            "menu_config, permissions, status) VALUES (?, ?, ?, ?, ?, ?, 1)",
            dto.getModuleCode(), dto.getModuleName(), dto.getVersion(),
            dto.getEntryUrl(), dto.getMenuConfig(), dto.getPermissions()
        );

        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_platform:module:edit')")
    @Auditable(module = "模块管理", actionType = "UPDATE", description = "编辑模块")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ModuleDTO dto) {
        int affected = jdbcTemplate.update(
            "UPDATE module_registry SET module_name = ?, version = ?, entry_url = ?, " +
            "menu_config = ?, permissions = ? WHERE id = ?",
            dto.getModuleName(), dto.getVersion(), dto.getEntryUrl(),
            dto.getMenuConfig(), dto.getPermissions(), id
        );
        if (affected == 0) {
            return Result.fail("模块不存在");
        }
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_platform:module:disable')")
    @Auditable(module = "模块管理", actionType = "UPDATE", description = "切换模块状态")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        Integer current = jdbcTemplate.queryForObject(
            "SELECT status FROM module_registry WHERE id = ?",
            Integer.class, id);
        if (current == null) {
            return Result.fail("模块不存在");
        }
        jdbcTemplate.update("UPDATE module_registry SET status = ? WHERE id = ?",
            current == 1 ? 0 : 1, id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_platform:module:disable')")
    @Auditable(module = "模块管理", actionType = "DELETE", description = "删除模块")
    public Result<Void> delete(@PathVariable Long id) {
        int affected = jdbcTemplate.update("DELETE FROM module_registry WHERE id = ?", id);
        if (affected == 0) {
            return Result.fail("模块不存在");
        }
        return Result.ok();
    }

    @Data
    public static class ModuleDTO {
        @NotBlank(message = "模块编码不能为空")
        private String moduleCode;
        @NotBlank(message = "模块名称不能为空")
        private String moduleName;
        @NotBlank(message = "版本号不能为空")
        private String version;
        @NotBlank(message = "入口地址不能为空")
        private String entryUrl;
        @NotBlank(message = "菜单配置不能为空")
        private String menuConfig;
        @NotBlank(message = "权限配置不能为空")
        private String permissions;
    }
}
