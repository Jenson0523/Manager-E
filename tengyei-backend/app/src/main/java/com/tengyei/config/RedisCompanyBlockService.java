package com.tengyei.config;

import com.tengyei.common.service.CompanyBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisCompanyBlockService implements CompanyBlockService {

    private static final String KEY_PREFIX = "company:blocked:";

    private final StringRedisTemplate redis;

    @Override
    public void block(Long companyId) {
        redis.opsForValue().set(KEY_PREFIX + companyId, "1");
    }

    @Override
    public void unblock(Long companyId) {
        redis.delete(KEY_PREFIX + companyId);
    }

    @Override
    public boolean isBlocked(Long companyId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + companyId));
    }
}
