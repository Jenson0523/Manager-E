package com.tengyei.org.controller;

import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.BranchSaveDTO;
import com.tengyei.org.dto.BranchVO;
import com.tengyei.org.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_branch:view')")
    public Result<PageResult<BranchVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(branchService.page(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_branch:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody BranchSaveDTO dto) {
        return Result.ok(Map.of("id", branchService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BranchSaveDTO dto) {
        branchService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        branchService.changeStatus(id, body.get("status"));
        return Result.ok();
    }
}
