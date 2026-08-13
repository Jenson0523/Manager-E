package com.tengyei.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
public class ModuleRegistryController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

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

    /**
     * 当前用户可见的已启用模块（前端据此渲染「业务应用」菜单并注册动态路由）。
     * 按模块声明的 permissions 过滤：持有其中任一权限点才可见，避免无权限用户点进去才撞 403。
     * 模块未声明权限点（permissions 为空/[]）时对所有登录用户可见，保持存量模块行为不变。
     */
    @GetMapping("/active")
    public Result<List<Map<String, Object>>> activeModules() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, module_code, module_name, version, entry_url, menu_config, " +
            "permissions, status FROM module_registry WHERE status = 1 ORDER BY id"
        );

        Set<String> authorities = currentAuthorities();
        boolean all = authorities.contains("PERM_*");

        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String permissions = (String) column(row, "permissions");
            if (!all && !moduleVisible(permissions, authorities)) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", column(row, "id"));
            m.put("moduleCode", column(row, "module_code"));
            m.put("moduleName", column(row, "module_name"));
            m.put("version", column(row, "version"));
            m.put("entryUrl", column(row, "entry_url"));
            m.put("menuConfig", column(row, "menu_config"));
            m.put("permissions", permissions);
            m.put("status", column(row, "status"));
            visible.add(m);
        }
        return Result.ok(visible);
    }

    /**
     * 列标签大小写随数据库方言而异(H2 会把未加引号的列名/别名全部大写),
     * 统一忽略大小写取值,避免"MySQL 上过滤生效、H2 上静默失效"这类只在某一侧暴露的问题。
     */
    private Object column(Map<String, Object> row, String columnName) {
        Object v = row.get(columnName);
        if (v != null) return v;
        v = row.get(columnName.toUpperCase());
        if (v != null) return v;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(columnName)) return e.getValue();
        }
        return null;
    }

    private Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }

    private boolean moduleVisible(String permissionsJson, Set<String> authorities) {
        if (permissionsJson == null || permissionsJson.isBlank()) return true;
        List<String> declared;
        try {
            declared = objectMapper.readValue(permissionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // 权限配置写坏了不应让模块整体消失,退回"可见",由接口层继续兜底鉴权
            return true;
        }
        if (declared.isEmpty()) return true;
        return declared.stream().anyMatch(p -> authorities.contains("PERM_" + p));
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
