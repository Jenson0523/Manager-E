package com.tengyei.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.rbac.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
