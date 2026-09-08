package com.tianji.message.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.message.config.MessageProperties;
import com.tianji.message.domain.dto.UserInboxDTO;
import com.tianji.message.domain.dto.UserInboxFormDTO;
import com.tianji.message.domain.po.NoticeTemplate;
import com.tianji.message.domain.po.PublicNotice;
import com.tianji.message.domain.po.UserInbox;
import com.tianji.message.domain.query.UserInboxQuery;
import com.tianji.message.enums.NoticeType;
import com.tianji.message.mapper.UserInboxMapper;
import com.tianji.message.service.IUserInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户通知记录服务实现类。
 */
@Service
@RequiredArgsConstructor
public class UserInboxServiceImpl extends ServiceImpl<UserInboxMapper, UserInbox> implements IUserInboxService {

    private static final int MAX_NOTICE_LOAD_SIZE = 200;

    private final MessageProperties properties;

    @Override
    public void saveNoticeToInbox(NoticeTemplate notice, List<UserDTO> users) {
        if (notice == null || CollUtils.isEmpty(users)) {
            return;
        }
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(properties.getMessageTtlMonths());
        List<UserInbox> list = new ArrayList<>(users.size());
        for (UserDTO user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            UserInbox box = new UserInbox();
            box.setTitle(notice.getTitle());
            box.setContent(notice.getContent());
            box.setUserId(user.getId());
            box.setType(notice.getType());
            box.setIsRead(false);
            box.setPushTime(pushTime);
            box.setExpireTime(expireTime);
            list.add(box);
        }
        if (CollUtils.isNotEmpty(list)) {
            saveBatch(list);
        }
    }

    @Override
    @Transactional
    public PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query) {
        Long userId = requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        loadPendingPublicNotices(userId, now);

        Page<UserInbox> userInboxPage = query.toMpPage("push_time", false);
        userInboxPage = lambdaQuery()
                .eq(UserInbox::getUserId, userId)
                .gt(UserInbox::getExpireTime, now)
                .eq(query.getIsRead() != null, UserInbox::getIsRead, query.getIsRead())
                .eq(query.getType() != null, UserInbox::getType, query.getType())
                .page(userInboxPage);
        return PageDTO.of(userInboxPage, UserInboxDTO.class);
    }

    /**
     * 将当前有效期内尚未进入用户收件箱的公告补齐。
     *
     * <p>不能只使用 pushTime 作为游标：同一时刻可能存在多条公告。Mapper 通过公告内容与
     * 用户现有收件箱记录做幂等过滤，再按发布时间和公告 ID 稳定排序，每次最多补齐 200 条。</p>
     */
    private void loadPendingPublicNotices(Long userId, LocalDateTime now) {
        LocalDateTime earliestTime = now.minusMonths(properties.getNoticeTtlMonths());
        List<PublicNotice> pending = getBaseMapper().queryPendingPublicNotices(
                userId, earliestTime, now, MAX_NOTICE_LOAD_SIZE);
        if (CollUtils.isEmpty(pending)) {
            return;
        }
        List<UserInbox> inboxes = new ArrayList<>(pending.size());
        for (PublicNotice notice : pending) {
            UserInbox box = new UserInbox();
            box.setTitle(notice.getTitle());
            box.setContent(notice.getContent());
            box.setUserId(userId);
            box.setType(notice.getType());
            box.setIsRead(false);
            box.setPushTime(notice.getPushTime());
            box.setExpireTime(notice.getExpireTime());
            inboxes.add(box);
        }
        saveBatch(inboxes);
    }

    @Override
    public Long sentMessageToUser(UserInboxFormDTO userInboxFormDTO) {
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(properties.getMessageTtlMonths());
        Long userId = requireCurrentUser();

        UserInbox inbox = new UserInbox();
        inbox.setUserId(userInboxFormDTO.getUserId());
        inbox.setContent(userInboxFormDTO.getContent());
        inbox.setType(NoticeType.PRIVATE_MESSAGE.getValue());
        inbox.setIsRead(false);
        inbox.setPushTime(pushTime);
        inbox.setExpireTime(expireTime);
        inbox.setPublisher(userId);
        save(inbox);
        return inbox.getId();
    }

    @Override
    public void markAsRead(Long id) {
        Long userId = requireCurrentUser();
        lambdaUpdate()
                .eq(UserInbox::getId, id)
                .eq(UserInbox::getUserId, userId)
                .gt(UserInbox::getExpireTime, LocalDateTime.now())
                .set(UserInbox::getIsRead, true)
                .update();
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        Long userId = requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        loadPendingPublicNotices(userId, now);
        lambdaUpdate()
                .eq(UserInbox::getUserId, userId)
                .eq(UserInbox::getIsRead, false)
                .gt(UserInbox::getExpireTime, now)
                .set(UserInbox::getIsRead, true)
                .update();
    }

    @Override
    @Transactional
    public Long countUnread() {
        Long userId = requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        loadPendingPublicNotices(userId, now);
        return lambdaQuery()
                .eq(UserInbox::getUserId, userId)
                .eq(UserInbox::getIsRead, false)
                .gt(UserInbox::getExpireTime, now)
                .count();
    }

    private Long requireCurrentUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }

}
