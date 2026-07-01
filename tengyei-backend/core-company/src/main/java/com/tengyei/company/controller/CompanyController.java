package com.tengyei.company.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.company.dto.CompanyCreateDTO;
import com.tengyei.company.dto.CompanyUpdateDTO;
import com.tengyei.company.dto.CompanyVO;
import com.tengyei.company.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:view')")
    public Result<PageResult<CompanyVO>> page(
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return Result.ok(companyService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:view')")
    public Result<CompanyVO> detail(@PathVariable("id") Long id) {
        return Result.ok(companyService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:create')")
    @Auditable(module = "企业管理", actionType = "CREATE", description = "新建企业")
    public Result<Map<String, Long>> create(@Valid @RequestBody CompanyCreateDTO dto) {
        Long id = companyService.create(dto);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:edit')")
    @Auditable(module = "企业管理", actionType = "UPDATE", description = "编辑企业信息")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody CompanyUpdateDTO dto) {
        companyService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:disable')")
    @Auditable(module = "企业管理", actionType = "UPDATE", description = "变更企业状态")
    public Result<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        companyService.changeStatus(id, body.get("status"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:disable')")
    @Auditable(module = "企业管理", actionType = "DELETE", description = "删除企业")
    public Result<Void> delete(@PathVariable("id") Long id) {
        companyService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reset-admin-password")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:edit')")
    @Auditable(module = "企业管理", actionType = "UPDATE", description = "重置企业管理员密码")
    public Result<Void> resetAdminPassword(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        companyService.resetAdminPassword(id, body.get("password"));
        return Result.ok();
    }
}
