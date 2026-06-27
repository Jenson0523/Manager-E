package com.tengyei.auth.controller;

import com.tengyei.auth.dto.LoginRequest;
import com.tengyei.auth.dto.LoginResponse;
import com.tengyei.auth.service.AuthService;
import com.tengyei.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest request) {
        return Result.ok(authService.login(req, getClientIp(request)));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null) {
            authService.logout(extractToken(authHeader));
        }
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<String> refresh(@RequestHeader("Authorization") String authHeader) {
        return Result.ok(authService.refresh(extractToken(authHeader)));
    }

    private String extractToken(String authHeader) {
        return authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : authHeader;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip.split(",")[0].trim();
    }
}
