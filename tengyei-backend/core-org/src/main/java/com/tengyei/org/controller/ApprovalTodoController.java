package com.tengyei.org.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.ApprovalDelegateDTO;
import com.tengyei.org.dto.ApprovalInstanceVO;
import com.tengyei.org.entity.WfDelegate;
import com.tengyei.org.service.ApprovalEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class ApprovalTodoController {

    private final ApprovalEngineService engineService;
    private final com.tengyei.org.service.ApprovalFlowService flowService;

    /** 发起人可选的启用表单(含字段定义) */
    @GetMapping("/forms")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:apply','PERM_platform:approval:apply')")
    public Result<List<com.tengyei.org.dto.ApprovalFlowVO>> forms() {
        return Result.ok(flowService.enabledForms());
    }

    /** 当前用户所属部门:多部门员工发起审批时选提交部门用 */
    @GetMapping("/my-depts")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:apply','PERM_platform:approval:apply')")
    public Result<List<java.util.Map<String, Object>>> myDepts() {
        return Result.ok(engineService.myDepts());
    }

    @GetMapping("/todo")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_platform:approval:view')")
    public Result<List<ApprovalInstanceVO>> todo() {
        return Result.ok(engineService.myTodo(TenantContext.getUserId()));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:apply','PERM_platform:approval:apply')")
    public Result<List<ApprovalInstanceVO>> my() {
        return Result.ok(engineService.myApplied(TenantContext.getUserId()));
    }

    /** 统计列表:按角色返回实例(管理员=全部,发起人=自己发起的,其他人=已审批的) */
    @GetMapping("/list")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_approval:apply','PERM_approval:manage','PERM_platform:approval:view','PERM_platform:approval:apply','PERM_platform:approval:manage')")
    public Result<List<ApprovalInstanceVO>> listForStats() {
        return Result.ok(engineService.listForStats());
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_approval:apply','PERM_approval:manage','PERM_platform:approval:view','PERM_platform:approval:apply','PERM_platform:approval:manage')")
    public Result<java.util.Map<String, Object>> statistics() {
        return Result.ok(engineService.statistics());
    }

    /** 统计卡片点击查看详情:按状态查询与当前用户相关的审批实例 */
    @GetMapping("/statistics/detail")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_approval:apply','PERM_approval:manage','PERM_platform:approval:view','PERM_platform:approval:apply','PERM_platform:approval:manage')")
    public Result<List<ApprovalInstanceVO>> statisticsDetail(@RequestParam(required = false) String status) {
        return Result.ok(engineService.myRelatedByStatus(TenantContext.getUserId(), status));
    }

    @GetMapping("/done")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_platform:approval:view')")
    public Result<List<ApprovalInstanceVO>> done() {
        return Result.ok(engineService.myDone(TenantContext.getUserId()));
    }

    /** 抄送我的 */
    @GetMapping("/cc")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:view','PERM_platform:approval:view')")
    public Result<List<ApprovalInstanceVO>> cc() {
        return Result.ok(engineService.myCc(TenantContext.getUserId()));
    }

    /** 审批数据导出(管理侧) */
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:manage','PERM_platform:approval:manage')")
    public void export(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var data = engineService.export();
        String fileName = java.net.URLEncoder.encode(
                "审批记录_" + java.time.LocalDate.now(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), com.tengyei.org.dto.ApprovalExportVO.class)
            .sheet("审批记录")
            .doWrite(data);
    }

    @GetMapping("/delegate")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:delegate','PERM_platform:approval:delegate')")
    public Result<WfDelegate> delegateGet() {
        return Result.ok(engineService.delegateGet(TenantContext.getUserId()));
    }

    @PutMapping("/delegate")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_approval:delegate','PERM_platform:approval:delegate')")
    @Auditable(module = "审批", actionType = "UPDATE", description = "设置审批代理")
    public Result<Void> delegateSave(@Valid @RequestBody ApprovalDelegateDTO dto) {
        engineService.delegateSave(TenantContext.getUserId(), TenantContext.getUserName(),
            dto.getDelegateId(), dto.getStartAt(), dto.getEndAt(), dto.getStatus());
        return Result.ok();
    }
}
