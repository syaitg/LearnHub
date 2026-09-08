package com.tianji.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.message.domain.po.PublicNotice;
import com.tianji.message.domain.po.UserInbox;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户通知记录 Mapper 接口。
 */
public interface UserInboxMapper extends BaseMapper<UserInbox> {

    List<PublicNotice> queryPendingPublicNotices(
            @Param("userId") Long userId,
            @Param("earliestTime") LocalDateTime earliestTime,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
}
