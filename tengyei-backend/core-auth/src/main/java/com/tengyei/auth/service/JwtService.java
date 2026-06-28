package com.tengyei.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expireHours;
    private final long refreshBeforeMinutes;

    public JwtService(
            @Value("${tengyei.jwt.secret}") String secret,
            @Value("${tengyei.jwt.expire-hours}") long expireHours,
            @Value("${tengyei.jwt.refresh-before-minutes}") long refreshBeforeMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
        this.refreshBeforeMinutes = refreshBeforeMinutes;
    }

    public String generate(Long tenantId, Long userId, Long branchId,
                           List<String> roleCodes, List<String> permissions, String dataScope) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("tenant_id", tenantId)
                .claim("user_id", userId)
                .claim("branch_id", branchId)
                .claim("role_codes", roleCodes)
                .claim("permissions", permissions)
                .claim("data_scope", dataScope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(secretKey)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            if (token == null || token.isBlank()) return false;
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean shouldRefresh(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            return expiration.toInstant().isBefore(
                    Instant.now().plusSeconds(refreshBeforeMinutes * 60));
        } catch (Exception e) {
            return false;
        }
    }

    public Long getTenantId(String token) {
        return toLong(getClaims(token).get("tenant_id"));
    }

    public Long getUserId(String token) {
        return toLong(getClaims(token).get("user_id"));
    }

    public Long getBranchId(String token) {
        Object val = getClaims(token).get("branch_id");
        return val == null ? null : toLong(val);
    }

    public String getDataScope(String token) {
        return (String) getClaims(token).get("data_scope");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        return (List<String>) getClaims(token).get("permissions");
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoleCodes(String token) {
        return (List<String>) getClaims(token).get("role_codes");
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
