package com.tengyei.company.controller;

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
@PreAuthorize("hasAuthority('PERM_*')")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public Result<PageResult<CompanyVO>> page(
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return Result.ok(companyService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<CompanyVO> detail(@PathVariable("id") Long id) {
        return Result.ok(companyService.detail(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CompanyCreateDTO dto) {
        Long id = companyService.create(dto);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody CompanyUpdateDTO dto) {
        companyService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        companyService.changeStatus(id, body.get("status"));
        return Result.ok();
    }
}
