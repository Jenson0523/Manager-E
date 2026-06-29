package com.tengyei.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.rbac.entity.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
