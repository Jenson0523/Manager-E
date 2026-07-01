package com.tengyei;

import com.tengyei.auth.service.TokenBlacklistService;
import com.tengyei.common.service.CompanyBlockService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public TokenBlacklistService tokenBlacklistService() {
        return new TokenBlacklistService(null) {
            @Override
            public void blacklist(String token, long ttlSeconds) {
            }

            @Override
            public boolean isBlacklisted(String token) {
                return false;
            }
        };
    }

    @Bean
    @Primary
    public CompanyBlockService companyBlockService() {
        return new CompanyBlockService() {
            @Override public void block(Long companyId) {}
            @Override public void unblock(Long companyId) {}
            @Override public boolean isBlocked(Long companyId) { return false; }
        };
    }
}
