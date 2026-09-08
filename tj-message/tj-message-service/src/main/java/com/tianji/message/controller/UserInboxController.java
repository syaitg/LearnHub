package com.tianji.message.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.message.domain.dto.UserInboxDTO;
import com.tianji.message.domain.dto.UserInboxFormDTO;
import com.tianji.message.domain.query.UserInboxQuery;
import com.tianji.message.service.IUserInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户收件箱接口。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/inboxes")
@Tag(name = "用户收件箱接口")
public class UserInboxController {

    private final IUserInboxService inboxService;

    @PostMapping
    @Operation(summary = "发送私信")
    public Long sentMessageToUser(@RequestBody UserInboxFormDTO userInboxFormDTO) {
        return inboxService.sentMessageToUser(userInboxFormDTO);
    }

    @GetMapping
    @Operation(summary = "分页查询收件箱")
    public PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query) {
        return inboxService.queryUserInBoxesPage(query);
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条消息为已读")
    public void markAsRead(@PathVariable Long id) {
        inboxService.markAsRead(id);
    }

    @PutMapping("/read-all")
    @Operation(summary = "标记全部消息为已读")
    public void markAllAsRead() {
        inboxService.markAllAsRead();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "查询未读消息数量")
    public Long countUnread() {
        return inboxService.countUnread();
    }
}
