package com.tengyei.auth.filter;

import com.tengyei.auth.service.JwtService;
import com.tengyei.auth.service.TokenBlacklistService;
import com.tengyei.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final Optional<TokenBlacklistService> blacklistService;

    @Autowired
    public JwtAuthFilter(JwtService jwtService, Optional<TokenBlacklistService> blacklistService) {
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            boolean blacklisted = blacklistService.map(svc -> svc.isBlacklisted(token)).orElse(false);
            if (token != null && jwtService.isValid(token) && !blacklisted) {
                Long tenantId = jwtService.getTenantId(token);
                Long userId = jwtService.getUserId(token);
                Long branchId = jwtService.getBranchId(token);
                String dataScope = jwtService.getDataScope(token);
                String realName = jwtService.getRealName(token);
                List<String> permissions = jwtService.getPermissions(token);

                TenantContext.setTenantId(tenantId);
                TenantContext.setUserId(userId);
                TenantContext.setBranchId(branchId);
                TenantContext.setDataScope(dataScope);
                TenantContext.setUserName(realName);

                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                        .collect(Collectors.toList());

                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignored) {
            // Invalid token — filter silently; endpoint will return 401
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
