package com.tianji.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.message.domain.dto.UserInboxDTO;
import com.tianji.message.domain.dto.UserInboxFormDTO;
import com.tianji.message.domain.po.NoticeTemplate;
import com.tianji.message.domain.po.UserInbox;
import com.tianji.message.domain.query.UserInboxQuery;

import java.util.List;

/**
 * 用户通知记录服务。
 */
public interface IUserInboxService extends IService<UserInbox> {

    void saveNoticeToInbox(NoticeTemplate noticeTemplate, List<UserDTO> users);

    PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query);

    Long sentMessageToUser(UserInboxFormDTO userInboxFormDTO);

    void markAsRead(Long id);

    void markAllAsRead();

    Long countUnread();
}
