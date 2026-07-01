package com.tengyei.common.service;

/**
 * 公司封禁标记服务接口：停用公司时标记，每次请求校验。
 * Redis 实现由 app 模块提供，测试时可注入空实现。
 */
public interface CompanyBlockService {

    void block(Long companyId);

    void unblock(Long companyId);

    boolean isBlocked(Long companyId);
}
