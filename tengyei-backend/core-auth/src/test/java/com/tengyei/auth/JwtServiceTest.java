package com.tengyei.auth;

import com.tengyei.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
            "TestSecretKey@2026#MustBe32CharsLongXX",
            2L,
            30L
        );
    }

    @Test
    void generate_and_parse_token() {
        String token = jwtService.generate(10001L, 5001L, 201L,
                List.of("branch_admin"), List.of("user:view"), "branch");

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.getTenantId(token)).isEqualTo(10001L);
        assertThat(jwtService.getUserId(token)).isEqualTo(5001L);
        assertThat(jwtService.getBranchId(token)).isEqualTo(201L);
        assertThat(jwtService.getDataScope(token)).isEqualTo("branch");
    }

    @Test
    void invalid_token_returns_false() {
        assertThat(jwtService.isValid("invalid.token.here")).isFalse();
        assertThat(jwtService.isValid(null)).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    void super_admin_token_has_tenant_zero_and_null_branch() {
        String token = jwtService.generate(0L, 1L, null,
                List.of("super_admin"), List.of(), "all");
        assertThat(jwtService.getTenantId(token)).isEqualTo(0L);
        assertThat(jwtService.getBranchId(token)).isNull();
    }

    @Test
    void permissions_round_trip() {
        List<String> perms = List.of("user:view", "user:create", "dept:view");
        String token = jwtService.generate(1L, 1L, null, List.of("admin"), perms, "all");
        assertThat(jwtService.getPermissions(token)).containsExactlyInAnyOrder(
                "user:view", "user:create", "dept:view");
    }
}
