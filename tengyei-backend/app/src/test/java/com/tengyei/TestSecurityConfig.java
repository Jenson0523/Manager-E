package com.tengyei;

import com.tengyei.auth.service.TokenBlacklistService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides a no-op TokenBlacklistService
 * so tests can run without a real Redis connection.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public TokenBlacklistService tokenBlacklistService() {
        return new TokenBlacklistService(null) {
            @Override
            public void blacklist(String token, long ttlSeconds) {
                // no-op in tests
            }

            @Override
            public boolean isBlacklisted(String token) {
                return false;
            }
        };
    }
}
