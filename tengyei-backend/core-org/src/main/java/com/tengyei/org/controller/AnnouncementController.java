package com.tengyei.org.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.AnnouncementSaveDTO;
import com.tengyei.org.entity.SysAnnouncement;
import com.tengyei.org.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 当前用户应展示的横幅(登录即可,含系统计算项) */
    @GetMapping("/active")
    public Result<List<Map<String, Object>>> active() {
        return Result.ok(announcementService.activeForMe(TenantContext.getUserId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_announcement:view','PERM_announcement:manage','PERM_announcement:disable'," +
        "'PERM_platform:announcement:view','PERM_platform:announcement:manage','PERM_platform:announcement:disable')")
    public Result<List<SysAnnouncement>> list() {
        return Result.ok(announcementService.list());
    }

    /** 详情:送达人员或本租户管理者可看(服务层校验可见性) */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(announcementService.detail(id, TenantContext.getUserId()));
    }

    /** 已读名单(管理侧) */
    @GetMapping("/{id}/reads")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_announcement:view','PERM_announcement:manage','PERM_announcement:disable'," +
        "'PERM_platform:announcement:view','PERM_platform:announcement:manage','PERM_platform:announcement:disable')")
    public Result<List<Map<String, Object>>> reads(@PathVariable Long id) {
        return Result.ok(announcementService.reads(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_announcement:manage','PERM_platform:announcement:manage')")
    @Auditable(module = "公告", actionType = "CREATE", description = "保存横幅公告")
    public Result<Map<String, Long>> save(@Valid @RequestBody AnnouncementSaveDTO dto) {
        return Result.ok(Map.of("id",
            announcementService.save(dto, TenantContext.getUserName())));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_announcement:disable','PERM_platform:announcement:disable')")
    @Auditable(module = "公告", actionType = "UPDATE", description = "停用/启用横幅公告")
    public Result<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        announcementService.setStatus(id, body.get("status"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_announcement:disable','PERM_platform:announcement:disable')")
    @Auditable(module = "公告", actionType = "DELETE", description = "删除横幅公告")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.ok();
    }
}
