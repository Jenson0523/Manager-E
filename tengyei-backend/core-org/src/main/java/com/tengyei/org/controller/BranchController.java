package com.tengyei.org.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.BranchSaveDTO;
import com.tengyei.org.dto.BranchVO;
import com.tengyei.org.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    @Auditable(module = "分公司管理", actionType = "CREATE", description = "新建分公司")
    public Result<Map<String, Long>> create(@Valid @RequestBody BranchSaveDTO dto) {
        return Result.ok(Map.of("id", branchService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    @Auditable(module = "分公司管理", actionType = "UPDATE", description = "编辑分公司信息")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BranchSaveDTO dto) {
        branchService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    @Auditable(module = "分公司管理", actionType = "UPDATE", description = "变更分公司状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        branchService.changeStatus(id, body.get("status"));
        return Result.ok();
    }

    // ======== 分公司-部门关联 ========

    /** 获取分公司关联的部门ID列表 */
    @GetMapping("/{branchId}/depts")
    @PreAuthorize("hasAuthority('PERM_branch:view')")
    public Result<List<Long>> getDepts(@PathVariable Long branchId) {
        return Result.ok(branchService.getDeptIds(branchId));
    }

    /** 批量关联部门到分公司 */
    @PostMapping("/{branchId}/depts")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    @Auditable(module = "分公司管理", actionType = "UPDATE", description = "关联部门到分公司")
    public Result<Void> linkDepts(@PathVariable Long branchId, @RequestBody Map<String, List<Long>> body) {
        branchService.linkDepts(branchId, body.get("deptIds"));
        return Result.ok();
    }

    /** 解除分公司与部门的关联 */
    @DeleteMapping("/{branchId}/depts/{deptId}")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    @Auditable(module = "分公司管理", actionType = "UPDATE", description = "解除分公司-部门关联")
    public Result<Void> unlinkDept(@PathVariable Long branchId, @PathVariable Long deptId) {
        branchService.unlinkDept(branchId, deptId);
        return Result.ok();
    }
}
