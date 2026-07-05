package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.org.entity.SysNotice;
import com.tengyei.org.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 站内消息:审批通知统一入口。发送失败不阻断业务(通知是尽力而为) */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeMapper noticeMapper;

    public void send(Long tenantId, Long userId, String type, String title, String content,
                     String bizType, Long bizId) {
        try {
            SysNotice n = new SysNotice();
            n.setTenantId(tenantId);
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            n.setIsRead(0);
            noticeMapper.insert(n);
        } catch (Exception ignored) {
            // 通知失败不影响主流程
        }
    }

    /** 我的消息,最近 50 条 */
    public List<SysNotice> myNotices(Long userId) {
        return noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getTenantId, TenantContext.getTenantId())
            .eq(SysNotice::getUserId, userId)
            .orderByDesc(SysNotice::getId)
            .last("LIMIT 50"));
    }

    public long unreadCount(Long userId) {
        return noticeMapper.selectCount(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getTenantId, TenantContext.getTenantId())
            .eq(SysNotice::getUserId, userId)
            .eq(SysNotice::getIsRead, 0));
    }

    public void markRead(Long userId, Long noticeId) {
        SysNotice n = noticeMapper.selectById(noticeId);
        if (n != null && n.getUserId().equals(userId)) {
            n.setIsRead(1);
            noticeMapper.updateById(n);
        }
    }

    public void markAllRead(Long userId) {
        SysNotice patch = new SysNotice();
        patch.setIsRead(1);
        noticeMapper.update(patch, new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getTenantId, TenantContext.getTenantId())
            .eq(SysNotice::getUserId, userId)
            .eq(SysNotice::getIsRead, 0));
    }

    /** 超时提醒去重:同一节点只提醒一次 */
    public boolean timeoutNoticeExists(Long nodeId) {
        return noticeMapper.selectCount(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getType, "APPROVAL_TIMEOUT")
            .eq(SysNotice::getBizType, "wf_node")
            .eq(SysNotice::getBizId, nodeId)) > 0;
    }
}
