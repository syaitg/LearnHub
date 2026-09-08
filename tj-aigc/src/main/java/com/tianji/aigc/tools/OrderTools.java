package com.tianji.aigc.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.PrePlaceOrder;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTools {

    private final CourseClient courseClient;
    private final TradeClient tradeClient;

    @Tool(description = Constant.Tools.PRE_PLACE_ORDER)
    public PrePlaceOrder prePlaceOrder(@ToolParam(description = Constant.ToolParams.COURSE_IDS) List<Number> courseIds,
                                       ToolContext toolContext) {
        List<Long> normalizedCourseIds = normalizeCourseIds(courseIds);
        if (CollUtil.isEmpty(normalizedCourseIds)) {
            return null;
        }

        // 在当前上下文中设置当前登录的用户id
        var userId = MapUtil.getLong(toolContext.getContext(), Constant.USER_ID);
        UserContext.setUser(userId);
        try {
            // 调用交易服务前，先通过课程服务校验课程是否真实存在且可以购买
            // 避免模型生成的错误课程编号直接请求交易服务
            List<CourseSimpleInfoDTO> courseInfos = Optional
                    .ofNullable(courseClient.getSimpleInfoList(normalizedCourseIds))
                    .orElseGet(List::of);
            Set<Long> existingIds = courseInfos.stream()
                    .filter(Objects::nonNull)
                    .map(CourseSimpleInfoDTO::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (existingIds.size() != normalizedCourseIds.size()
                    || !existingIds.containsAll(normalizedCourseIds)) {
                log.warn("跳过预下单，部分课程不存在，请求课程id={}，实际存在课程id={}",
                        normalizedCourseIds, existingIds);
                return null;
            }
            LocalDateTime now = LocalDateTime.now();
            boolean hasUnavailableCourse = courseInfos.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(course -> !CourseStatus.SHELF.equalsValue(course.getStatus())
                            || course.getPurchaseEndTime() == null
                            || course.getPurchaseEndTime().isBefore(now));
            if (hasUnavailableCourse) {
                log.warn("跳过预下单，部分课程当前不可购买，请求课程id={}",
                        normalizedCourseIds);
                return null;
            }

            // 调用预下单接口
            var orderConfirmVO = this.tradeClient.prePlaceOrder(normalizedCourseIds);
            boolean freeCourse = normalizedCourseIds.size() == 1
                    && courseInfos.size() == 1
                    && Boolean.TRUE.equals(courseInfos.get(0).getFree());
            return Optional.ofNullable(orderConfirmVO)
                    .map(confirm -> PrePlaceOrder.of(confirm, freeCourse))
                    .map(prePlaceOrder -> {
                        // 获取请求id
                        var requestId = MapUtil.getStr(toolContext.getContext(), Constant.REQUEST_ID);
                        // 数据标识
                        var field = StrUtil.lowerFirst(PrePlaceOrder.class.getSimpleName());
                        // 数据存储到保持器中
                        ToolResultHolder.put(requestId, field, prePlaceOrder);
                        return prePlaceOrder;
                    })
                    .orElse(null);
        } catch (RuntimeException e) {
            log.error("课程校验或预下单失败，课程id={}", normalizedCourseIds, e);
            return null;
        } finally {
            UserContext.removeUser();
        }
    }

    private List<Long> normalizeCourseIds(List<Number> courseIds) {
        if (CollUtil.isEmpty(courseIds)) {
            return List.of();
        }
        if (courseIds.stream().anyMatch(id -> id == null || id.longValue() <= 0)) {
            return List.of();
        }
        return courseIds.stream()
                .map(Number::longValue)
                .distinct()
                .toList();
    }

}
