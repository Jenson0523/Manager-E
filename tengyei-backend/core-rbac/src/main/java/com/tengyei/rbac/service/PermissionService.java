package com.tengyei.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.rbac.dto.PermissionGroupVO;
import com.tengyei.rbac.entity.Permission;
import com.tengyei.rbac.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;

    public List<PermissionGroupVO> grouped() {
        String tier = com.tengyei.common.context.TenantContext.isSuperAdmin() ? "platform" : "company";
        List<Permission> all = permissionMapper.selectList(
            new LambdaQueryWrapper<Permission>()
                .eq(Permission::getStatus, 1)
                .eq(Permission::getTier, tier)
                .orderByAsc(Permission::getSortOrder));
        Map<String, List<PermissionGroupVO.Item>> byModule = new LinkedHashMap<>();
        for (Permission p : all) {
            byModule.computeIfAbsent(p.getModule(), k -> new ArrayList<>())
                    .add(PermissionGroupVO.Item.builder()
                            .id(p.getId()).code(p.getCode()).name(p.getName()).build());
        }
        List<PermissionGroupVO> groups = new ArrayList<>();
        byModule.forEach((module, items) ->
            groups.add(PermissionGroupVO.builder().module(module).permissions(items).build()));
        return groups;
    }
}
