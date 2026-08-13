package com.tengyei.org.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.ContractSaveDTO;
import com.tengyei.org.dto.ContractVO;
import com.tengyei.org.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 合同台账。三档权限:查看 / 新增编辑 / 作废删除;可见范围由 data_scope 决定(见 ContractService) */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private static final String VIEW = "hasAnyAuthority('PERM_*','PERM_contract:view'," +
        "'PERM_contract:manage','PERM_contract:disable')";

    private final ContractService contractService;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<PageResult<ContractVO>> page(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Boolean expiring) {
        return Result.ok(contractService.page(page, size, keyword, type, status, expiring));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW)
    public Result<Map<String, Object>> summary() {
        Map<String, Long> counts = contractService.summary();
        return Result.ok(Map.of(
            "total", counts.get("total"),
            "expiring", counts.get("expiring"),
            "expired", counts.get("expired"),
            "remindDays", (long) contractService.getExpireRemindDays()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<ContractVO> detail(@PathVariable Long id) {
        return Result.ok(contractService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_contract:manage')")
    @Auditable(module = "合同", actionType = "CREATE", description = "保存合同")
    public Result<Map<String, Long>> save(@Valid @RequestBody ContractSaveDTO dto) {
        return Result.ok(Map.of("id", contractService.save(dto, TenantContext.getUserName())));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_contract:manage','PERM_contract:disable')")
    @Auditable(module = "合同", actionType = "UPDATE", description = "变更合同状态")
    public Result<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        contractService.setStatus(id, body.get("status"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_contract:disable')")
    @Auditable(module = "合同", actionType = "DELETE", description = "删除合同")
    public Result<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return Result.ok();
    }
}
