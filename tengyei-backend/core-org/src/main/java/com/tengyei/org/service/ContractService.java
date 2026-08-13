package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.ContractSaveDTO;
import com.tengyei.org.dto.ContractVO;
import com.tengyei.org.entity.Contract;
import com.tengyei.org.entity.Dept;
import com.tengyei.org.mapper.ContractMapper;
import com.tengyei.org.mapper.DeptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合同台账。数据可见范围复用角色上的 data_scope(all/branch/dept/self)，
 * 读列表与写操作走同一套口径（{@link #applyDataScope} / {@link #assertInScope}），避免"列表看不到但能改"的越权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    /** 履约中的合同才需要关心到期；草稿/已完成/已终止不参与临期计算与提醒 */
    private static final Set<String> ACTIVE_STATUS = Set.of("EFFECTIVE", "PERFORMING");
    /** 终止＝作废，与删除同属 contract:disable，不能被 contract:manage 顺手做掉 */
    private static final String TERMINATED = "TERMINATED";
    private static final Set<String> ALL_STATUS =
        Set.of("DRAFT", "EFFECTIVE", "PERFORMING", "COMPLETED", "TERMINATED");
    private static final Set<String> ALL_TYPE =
        Set.of("SALE", "PURCHASE", "SERVICE", "LEASE", "OTHER");

    private final ContractMapper contractMapper;
    private final DeptMapper deptMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tengyei.contract.expire-remind-days:30}")
    private int expireRemindDays;

    public int getExpireRemindDays() {
        return expireRemindDays;
    }

    /* ---------------- 查询 ---------------- */

    public PageResult<ContractVO> page(long page, long size, String keyword, String type,
                                       String status, Boolean expiring) {
        LambdaQueryWrapper<Contract> qw = new LambdaQueryWrapper<>();
        // 显式限定租户：超管走 ignoreTable 不会被拦截器加条件，这里补上避免跨租户读到合同
        qw.eq(Contract::getTenantId, TenantContext.getTenantId());

        if (!applyDataScope(qw)) {
            return PageResult.of(List.of(), 0, page, size);
        }

        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Contract::getName, keyword)
                .or().like(Contract::getContractNo, keyword)
                .or().like(Contract::getPartyB, keyword));
        }
        if (StringUtils.hasText(type)) {
            qw.eq(Contract::getType, type);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(Contract::getStatus, status);
        }
        if (Boolean.TRUE.equals(expiring)) {
            qw.in(Contract::getStatus, ACTIVE_STATUS)
                .isNotNull(Contract::getEndDate)
                .le(Contract::getEndDate, LocalDate.now().plusDays(expireRemindDays));
        }
        qw.orderByDesc(Contract::getId);

        Page<Contract> p = contractMapper.selectPage(new Page<>(page, size), qw);
        PageResult<ContractVO> result = PageResult.from(p, this::toVO);
        fillDisplayNames(result.getRecords());
        return result;
    }

    /** 列表页顶部统计：总数 / 临期 / 已过期，口径与列表一致 */
    public Map<String, Long> summary() {
        LambdaQueryWrapper<Contract> base = new LambdaQueryWrapper<>();
        base.eq(Contract::getTenantId, TenantContext.getTenantId());
        if (!applyDataScope(base)) {
            return Map.of("total", 0L, "expiring", 0L, "expired", 0L);
        }
        long total = contractMapper.selectCount(base);

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Contract> expiring = new LambdaQueryWrapper<>();
        expiring.eq(Contract::getTenantId, TenantContext.getTenantId());
        applyDataScope(expiring);
        expiring.in(Contract::getStatus, ACTIVE_STATUS)
            .isNotNull(Contract::getEndDate)
            .ge(Contract::getEndDate, today)
            .le(Contract::getEndDate, today.plusDays(expireRemindDays));

        LambdaQueryWrapper<Contract> expired = new LambdaQueryWrapper<>();
        expired.eq(Contract::getTenantId, TenantContext.getTenantId());
        applyDataScope(expired);
        expired.in(Contract::getStatus, ACTIVE_STATUS)
            .isNotNull(Contract::getEndDate)
            .lt(Contract::getEndDate, today);

        return Map.of(
            "total", total,
            "expiring", contractMapper.selectCount(expiring),
            "expired", contractMapper.selectCount(expired));
    }

    public ContractVO detail(Long id) {
        Contract c = requireContract(id);
        assertInScope(c);
        ContractVO vo = toVO(c);
        fillDisplayNames(List.of(vo));
        return vo;
    }

    /* ---------------- 写入 ---------------- */

    @Transactional
    public Long save(ContractSaveDTO dto, String operatorName) {
        validateEnums(dto);
        if (dto.getStartDate() != null && dto.getEndDate() != null
            && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(422, "到期日期不能早于生效日期");
        }

        Long tenantId = TenantContext.getTenantId();
        Contract c;
        String oldStatus = null;
        if (dto.getId() != null) {
            c = requireContract(dto.getId());
            assertInScope(c);
            oldStatus = c.getStatus();
        } else {
            c = new Contract();
            c.setTenantId(tenantId);
            c.setCreatedBy(operatorName);
        }

        // 负责人默认落到经办人自己，否则 self 范围的人一存就看不见自己刚建的合同
        Long ownerId = dto.getOwnerId() != null ? dto.getOwnerId() : TenantContext.getUserId();
        assertOwnerInTenant(ownerId, tenantId);
        c.setOwnerId(ownerId);
        fillOwnerOrg(c, ownerId);

        String no = StringUtils.hasText(dto.getContractNo())
            ? dto.getContractNo().trim()
            : (c.getContractNo() != null ? c.getContractNo() : nextContractNo(tenantId));
        assertNoNotTaken(tenantId, no, dto.getId());
        c.setContractNo(no);

        c.setName(dto.getName().trim());
        c.setType(StringUtils.hasText(dto.getType()) ? dto.getType() : "SALE");
        c.setPartyB(dto.getPartyB().trim());
        c.setPartyBContact(dto.getPartyBContact());
        c.setPartyBPhone(dto.getPartyBPhone());
        c.setAmount(dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO);
        c.setSignDate(dto.getSignDate());
        c.setStartDate(dto.getStartDate());
        c.setEndDate(dto.getEndDate());
        String newStatus = StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "DRAFT";
        assertCanTerminate(oldStatus, newStatus);
        c.setStatus(newStatus);
        c.setRemark(dto.getRemark());
        c.setAttachments(writeAttachments(dto.getAttachments()));

        // 到期日改动后重新允许提醒（否则改成新日期仍被旧的去重标记压住）
        c.setExpireNotifiedOn(null);

        if (c.getId() == null) {
            contractMapper.insert(c);
        } else {
            contractMapper.updateById(c);
        }
        return c.getId();
    }

    @Transactional
    public void setStatus(Long id, String status) {
        if (!ALL_STATUS.contains(status)) {
            throw new BusinessException(422, "非法的合同状态:" + status);
        }
        Contract c = requireContract(id);
        assertInScope(c);
        assertCanTerminate(c.getStatus(), status);
        c.setStatus(status);
        contractMapper.updateById(c);
    }

    @Transactional
    public void delete(Long id) {
        Contract c = requireContract(id);
        assertInScope(c);
        contractMapper.deleteById(id);
    }

    /* ---------------- 数据范围 ---------------- */

    /**
     * 给查询条件追加 data_scope 过滤。
     *
     * @return false 表示当前用户在该范围下不可能看到任何合同（调用方应直接返回空集）
     */
    private boolean applyDataScope(LambdaQueryWrapper<Contract> qw) {
        if (TenantContext.isSuperAdmin()) return true;
        String scope = TenantContext.getDataScope();
        if (scope == null || "all".equals(scope)) return true;

        if ("self".equals(scope)) {
            qw.eq(Contract::getOwnerId, TenantContext.getUserId());
            return true;
        }
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId == null) return false;
            qw.eq(Contract::getOwnerBranchId, branchId);
            return true;
        }
        if ("dept".equals(scope)) {
            Set<Long> allowed = allowedDeptIds();
            if (allowed.isEmpty()) return false;
            // 本人经办的合同始终可见，避免未分配部门/跨部门经办时自己的单子丢失
            qw.and(w -> w.in(Contract::getOwnerDeptId, allowed)
                .or().eq(Contract::getOwnerId, TenantContext.getUserId()));
            return true;
        }
        return true;
    }

    /** 写操作范围校验，口径与 {@link #applyDataScope} 一致 */
    private void assertInScope(Contract c) {
        if (TenantContext.isSuperAdmin()) return;
        String scope = TenantContext.getDataScope();
        if (scope == null || "all".equals(scope)) return;

        Long currentUserId = TenantContext.getUserId();
        if ("self".equals(scope)) {
            if (!currentUserId.equals(c.getOwnerId())) {
                throw new BusinessException(403, "无权操作该范围外的合同");
            }
            return;
        }
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId == null || !branchId.equals(c.getOwnerBranchId())) {
                throw new BusinessException(403, "无权操作该范围外的合同");
            }
            return;
        }
        if ("dept".equals(scope)) {
            boolean hit = c.getOwnerDeptId() != null && allowedDeptIds().contains(c.getOwnerDeptId());
            if (!hit && currentUserId.equals(c.getOwnerId())) hit = true;
            if (!hit) {
                throw new BusinessException(403, "无权操作该范围外的合同");
            }
        }
    }

    /** 当前用户所在部门及其所有子部门 */
    private Set<Long> allowedDeptIds() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Set.of();
        List<Long> myDepts = jdbcTemplate.queryForList(
            "SELECT dept_id FROM user_dept WHERE user_id = ?", Long.class, userId);
        Set<Long> all = new LinkedHashSet<>();
        for (Long deptId : myDepts) {
            all.addAll(collectSubDeptIds(deptId));
        }
        return all;
    }

    private Set<Long> collectSubDeptIds(Long parentId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(parentId);
        List<Dept> children = deptMapper.selectList(
            new LambdaQueryWrapper<Dept>().eq(Dept::getParentId, parentId));
        for (Dept child : children) {
            ids.addAll(collectSubDeptIds(child.getId()));
        }
        return ids;
    }

    /* ---------------- 辅助 ---------------- */

    private Contract requireContract(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null || !TenantContext.getTenantId().equals(c.getTenantId())) {
            throw new BusinessException(404, "合同不存在");
        }
        return c;
    }

    /**
     * 终止合同等同作废，须持 contract:disable。控制器为兼顾「生效/完成」放行了 contract:manage，
     * 这里再按目标状态收紧；只拦「转入终止」，已终止的合同改备注等不受影响。
     */
    private void assertCanTerminate(String oldStatus, String newStatus) {
        if (!TERMINATED.equals(newStatus) || TERMINATED.equals(oldStatus)) return;
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        boolean allowed = auth != null && auth.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .anyMatch(x -> "PERM_*".equals(x) || "PERM_contract:disable".equals(x));
        if (!allowed) {
            throw new BusinessException(403, "终止合同需要「作废/删除合同」权限");
        }
    }

    /** 负责人必须是本租户在职用户，否则会把他租户员工的部门/分支甚至姓名带进本租户合同 */
    private void assertOwnerInTenant(Long ownerId, Long tenantId) {
        if (ownerId == null) return;
        Long cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE id = ? AND tenant_id = ? AND is_deleted = 0",
            Long.class, ownerId, tenantId);
        if (cnt == null || cnt == 0) {
            throw new BusinessException(422, "负责人不存在或不属于本企业");
        }
    }

    private void validateEnums(ContractSaveDTO dto) {
        if (StringUtils.hasText(dto.getType()) && !ALL_TYPE.contains(dto.getType())) {
            throw new BusinessException(422, "非法的合同类型:" + dto.getType());
        }
        if (StringUtils.hasText(dto.getStatus()) && !ALL_STATUS.contains(dto.getStatus())) {
            throw new BusinessException(422, "非法的合同状态:" + dto.getStatus());
        }
    }

    /** 合同编号租户内唯一（软删记录不占号） */
    private void assertNoNotTaken(Long tenantId, String no, Long selfId) {
        LambdaQueryWrapper<Contract> qw = new LambdaQueryWrapper<Contract>()
            .eq(Contract::getTenantId, tenantId)
            .eq(Contract::getContractNo, no);
        if (selfId != null) {
            qw.ne(Contract::getId, selfId);
        }
        if (contractMapper.selectCount(qw) > 0) {
            throw new BusinessException(422, "合同编号已存在:" + no);
        }
    }

    private String nextContractNo(Long tenantId) {
        String prefix = "HT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 与 assertNoNotTaken(走 MyBatis-Plus,自动排除软删)口径一致:软删记录不占号
        Integer used = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM biz_contract WHERE tenant_id = ? AND is_deleted = 0 AND contract_no LIKE ?",
            Integer.class, tenantId, prefix + "%");
        int seq = (used == null ? 0 : used) + 1;
        String candidate = prefix + String.format("%03d", seq);
        // 号段可能因删除/手填出现空洞造成撞号，顺延到第一个可用号
        while (contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
            .eq(Contract::getTenantId, tenantId)
            .eq(Contract::getContractNo, candidate)) > 0) {
            candidate = prefix + String.format("%03d", ++seq);
        }
        return candidate;
    }

    /** 归属部门/分支按负责人当前所属带出，data_scope=dept/branch 的过滤依赖这两列 */
    private void fillOwnerOrg(Contract c, Long ownerId) {
        if (ownerId == null) {
            c.setOwnerDeptId(null);
            c.setOwnerBranchId(null);
            return;
        }
        // ORDER BY 保证多部门员工每次落到同一个部门,否则归属部门会在多次保存间漂移,
        // 连带 data_scope=dept 的可见性时有时无。多部门场景下由哪个部门归属仍是产品问题(见审批的"选提交部门")。
        List<Long> deptIds = jdbcTemplate.queryForList(
            "SELECT dept_id FROM user_dept WHERE user_id = ? ORDER BY dept_id", Long.class, ownerId);
        c.setOwnerDeptId(deptIds.isEmpty() ? null : deptIds.get(0));
        List<Long> branchIds = jdbcTemplate.queryForList(
            "SELECT branch_id FROM `user` WHERE id = ? AND is_deleted = 0", Long.class, ownerId);
        c.setOwnerBranchId(branchIds.isEmpty() ? null : branchIds.get(0));
    }

    private String writeAttachments(List<ContractSaveDTO.Attachment> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BusinessException(422, "附件格式不正确");
        }
    }

    private List<ContractSaveDTO.Attachment> readAttachments(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ContractSaveDTO.Attachment>>() {});
        } catch (Exception e) {
            log.warn("合同附件解析失败, contract attachments={}", json, e);
            return List.of();
        }
    }

    private ContractVO toVO(Contract c) {
        ContractVO vo = new ContractVO();
        vo.setId(c.getId());
        vo.setContractNo(c.getContractNo());
        vo.setName(c.getName());
        vo.setType(c.getType());
        vo.setPartyB(c.getPartyB());
        vo.setPartyBContact(c.getPartyBContact());
        vo.setPartyBPhone(c.getPartyBPhone());
        vo.setAmount(c.getAmount());
        vo.setSignDate(c.getSignDate());
        vo.setStartDate(c.getStartDate());
        vo.setEndDate(c.getEndDate());
        vo.setOwnerId(c.getOwnerId());
        vo.setOwnerDeptId(c.getOwnerDeptId());
        vo.setStatus(c.getStatus());
        vo.setRemark(c.getRemark());
        vo.setAttachments(readAttachments(c.getAttachments()));
        vo.setCreatedBy(c.getCreatedBy());
        vo.setCreatedAt(c.getCreatedAt());

        // 负责人/部门名称由 fillDisplayNames 整页批量补齐,避免逐行查询造成 N+1
        if (c.getEndDate() != null && ACTIVE_STATUS.contains(c.getStatus())) {
            vo.setDaysToExpire(ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate()));
        }
        return vo;
    }

    /** 整页一次性补齐负责人与归属部门名称:两条 IN 查询替代原先每行两条 */
    private void fillDisplayNames(List<ContractVO> vos) {
        if (vos.isEmpty()) return;
        Long tenantId = TenantContext.getTenantId();

        Map<Long, String> userNames = nameMap(
            "SELECT id, real_name FROM `user` WHERE is_deleted = 0 AND tenant_id = ? AND id IN (%s)",
            tenantId, vos.stream().map(ContractVO::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> deptNames = nameMap(
            "SELECT id, name FROM dept WHERE is_deleted = 0 AND tenant_id = ? AND id IN (%s)",
            tenantId, vos.stream().map(ContractVO::getOwnerDeptId).filter(Objects::nonNull).collect(Collectors.toSet()));

        for (ContractVO vo : vos) {
            if (vo.getOwnerId() != null) vo.setOwnerName(userNames.get(vo.getOwnerId()));
            if (vo.getOwnerDeptId() != null) vo.setOwnerDeptName(deptNames.get(vo.getOwnerDeptId()));
        }
    }

    /** id 均来自数据库的 Long,直接内联进 IN 无注入风险(与 UserService 的 inSql 同做法) */
    private Map<Long, String> nameMap(String sqlTemplate, Long tenantId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String inClause = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, String> map = new HashMap<>();
        jdbcTemplate.query(String.format(sqlTemplate, inClause),
            rs -> { map.put(rs.getLong(1), rs.getString(2)); },
            tenantId);
        return map;
    }
}
