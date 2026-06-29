package com.tengyei.rbac.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PermissionGroupVO {
    private String module;
    private List<Item> permissions;

    @Getter
    @Builder
    public static class Item {
        private Long id;
        private String code;
        private String name;
    }
}
