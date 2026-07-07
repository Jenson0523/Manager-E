package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.AnnouncementSaveDTO;
import com.tengyei.org.entity.SysAnnouncement;
import com.tengyei.org.mapper.SysAnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 横幅公告:存量公告(平台/公司发布) + 计算型系统横幅(审批待办/企业到期,现算不落库) */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final SysAnnouncementMapper announcementMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 当前用户应看到的横幅(含系统计算项) */
    public List<Map<String, Object>> activeForMe(Long userId) {
        Long tenantId = TenantContext.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> banners = new ArrayList<>();

        // 1) 本租户自己发布的(再按 部门/角色 定向过滤)
        List<SysAnnouncement> stored = announcementMapper.selectList(
            new LambdaQueryWrapper<SysAnnouncement>()
                .eq(SysAnnouncement::getTenantId, tenantId)
                .eq(SysAnnouncement::getTargetScope, "SELF")
                .eq(SysAnnouncement::getStatus, 1))
            .stream().filter(a -> matchesAudience(a, userId)).collect(Collectors.toCollection(ArrayList::new));

        // 2) 平台定向发给企业的(公司用户才收)
        if (tenantId != null && tenantId != 0L) {
            List<SysAnnouncement> fromPlatform = announcementMapper.selectList(
                new LambdaQueryWrapper<SysAnnouncement>()
                    .eq(SysAnnouncement::getTenantId, 0L)
                    .ne(SysAnnouncement::getTargetScope, "SELF")
                    .eq(SysAnnouncement::getStatus, 1));
            for (SysAnnouncement a : fromPlatform) {
                if ("ALL_COMPANIES".equals(a.getTargetScope())) {
                    stored.add(a);
                } else if ("COMPANIES".equals(a.getTargetScope()) && a.getTargetIds() != null
                        && Arrays.asList(a.getTargetIds().split(",")).contains(String.valueOf(tenantId))) {
                    stored.add(a);
                }
            }
        }
        stored.stream()
            .filter(a -> (a.getStartAt() == null || !a.getStartAt().isAfter(now))
                      && (a.getEndAt() == null || !a.getEndAt().isBefore(now)))
            .sorted((x, y) -> y.getId().compareTo(x.getId()))
            // 发布的公告点击统一进通知管理;系统计算横幅在下方各自带跳转
            .forEach(a -> banners.add(banner(a.getId(), a.getTitle(), a.getContent(), a.getLevel(), "/announcements")));

        // 3) 系统计算:待办审批(有超时则升级为紧急)
        Map<String, Object> todo = jdbcTemplate.queryForList(
            "SELECT COUNT(*) AS c, SUM(CASE WHEN due_at IS NOT NULL AND due_at < NOW() THEN 1 ELSE 0 END) AS o " +
            "FROM wf_node WHERE tenant_id = ? AND approver_id = ? AND status = 'APPROVING'",
            tenantId, userId).get(0);
        long todoCount = ((Number) todo.get("c")).longValue();
        long overdue = todo.get("o") == null ? 0 : ((Number) todo.get("o")).longValue();
        if (todoCount > 0) {
            banners.add(banner(null, "你有 " + todoCount + " 条待办审批" + (overdue > 0 ? "(" + overdue + " 条已超时)" : ""),
                null, overdue > 0 ? "URGENT" : "WARN", "/company/approval"));
        }

        // 4) 系统计算:到期提醒(公司=自己临期;平台=临期企业数)
        LocalDate in30 = LocalDate.now().plusDays(30);
        if (tenantId != null && tenantId != 0L) {
            List<java.sql.Date> rows = jdbcTemplate.queryForList(
                "SELECT expire_date FROM company WHERE id = ? AND is_deleted = 0 " +
                "AND expire_date IS NOT NULL AND expire_date >= CURRENT_DATE AND expire_date <= ?",
                java.sql.Date.class, tenantId, java.sql.Date.valueOf(in30));
            if (!rows.isEmpty()) {
                LocalDate expire = rows.get(0).toLocalDate();
                boolean urgent = !expire.isAfter(LocalDate.now().plusDays(7));
                banners.add(banner(null, "企业服务将于 " + expire + " 到期,请及时联系平台续期",
                    null, urgent ? "URGENT" : "WARN", null));
            }
        } else if (tenantId != null) {
            Long expiring = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE is_deleted = 0 AND status = 1 " +
                "AND expire_date IS NOT NULL AND expire_date >= CURRENT_DATE AND expire_date <= ?",
                Long.class, java.sql.Date.valueOf(in30));
            if (expiring != null && expiring > 0) {
                banners.add(banner(null, expiring + " 家企业将在 30 天内到期", null, "WARN", "/admin/companies"));
            }
        }
        return banners;
    }

    /** 租户内定向:ALL 或 用户属于指定 部门/角色 */
    private boolean matchesAudience(SysAnnouncement a, Long userId) {
        String type = a.getAudienceType();
        if (type == null || "ALL".equals(type) || a.getAudienceIds() == null) return true;
        String table = "DEPT".equals(type) ? "user_dept" : "user_role";
        String col = "DEPT".equals(type) ? "dept_id" : "role_id";
        List<Long> mine = jdbcTemplate.queryForList(
            "SELECT " + col + " FROM " + table + " WHERE user_id = ?", Long.class, userId);
        return Arrays.stream(a.getAudienceIds().split(","))
            .anyMatch(id -> mine.contains(Long.valueOf(id.trim())));
    }

    /** 管理列表:本租户发布的全部公告 */
    public List<SysAnnouncement> list() {
        return announcementMapper.selectList(new LambdaQueryWrapper<SysAnnouncement>()
            .eq(SysAnnouncement::getTenantId, TenantContext.getTenantId())
            .orderByDesc(SysAnnouncement::getId));
    }

    public Long save(AnnouncementSaveDTO dto, String operatorName) {
        Long tenantId = TenantContext.getTenantId();
        String scope = dto.getTargetScope() != null ? dto.getTargetScope() : "SELF";
        // 公司只能发本租户;定向企业仅平台可用
        if (tenantId != 0L && !"SELF".equals(scope)) {
            throw new BusinessException(403, "企业只能发布本公司通知");
        }
        if (!List.of("SELF", "ALL_COMPANIES", "COMPANIES").contains(scope)) {
            throw new BusinessException(422, "目标范围无效");
        }
        if ("COMPANIES".equals(scope) && (dto.getTargetIds() == null || dto.getTargetIds().isEmpty())) {
            throw new BusinessException(422, "请选择目标企业");
        }
        // 发给企业(整司)时不做租户内定向;仅本单位发布可选部门/角色
        String audience = "SELF".equals(scope) && dto.getAudienceType() != null ? dto.getAudienceType() : "ALL";
        if (!List.of("ALL", "DEPT", "ROLE").contains(audience)) {
            throw new BusinessException(422, "接收范围无效");
        }
        if (!"ALL".equals(audience)
                && (dto.getAudienceIds() == null || dto.getAudienceIds().isEmpty())) {
            throw new BusinessException(422, "请选择接收部门或角色");
        }

        SysAnnouncement a;
        if (dto.getId() != null) {
            a = announcementMapper.selectById(dto.getId());
            if (a == null || !a.getTenantId().equals(tenantId)) {
                throw new BusinessException(404, "公告不存在");
            }
        } else {
            a = new SysAnnouncement();
            a.setTenantId(tenantId);
            a.setCreatedBy(operatorName);
        }
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setLevel(dto.getLevel() != null ? dto.getLevel() : "INFO");
        a.setTargetScope(scope);
        a.setTargetIds("COMPANIES".equals(scope)
            ? dto.getTargetIds().stream().map(String::valueOf).collect(Collectors.joining(","))
            : null);
        a.setAudienceType(audience);
        a.setAudienceIds("ALL".equals(audience) ? null
            : dto.getAudienceIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        a.setStartAt(dto.getStartAt());
        a.setEndAt(dto.getEndAt());
        a.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        if (a.getId() != null) announcementMapper.updateById(a);
        else announcementMapper.insert(a);
        return a.getId();
    }

    public void delete(Long id) {
        SysAnnouncement a = announcementMapper.selectById(id);
        if (a == null || !a.getTenantId().equals(TenantContext.getTenantId())) {
            throw new BusinessException(404, "公告不存在");
        }
        announcementMapper.deleteById(id);
    }

    private Map<String, Object> banner(Long id, String title, String content, String level, String linkUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("content", content);
        m.put("level", level);
        m.put("linkUrl", linkUrl);
        return m;
    }
}
