package com.tengyei.org.controller;

import com.tengyei.common.response.Result;
import com.tengyei.org.dto.DeptSaveDTO;
import com.tengyei.org.dto.DeptTreeVO;
import com.tengyei.org.service.DeptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('PERM_dept:view')")
    public Result<List<DeptTreeVO>> tree() {
        return Result.ok(deptService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_dept:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody DeptSaveDTO dto) {
        return Result.ok(Map.of("id", deptService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_dept:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptSaveDTO dto) {
        deptService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_dept:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
