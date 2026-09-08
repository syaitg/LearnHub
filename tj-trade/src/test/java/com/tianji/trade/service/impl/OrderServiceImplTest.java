package com.tianji.trade.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.client.promotion.PromotionCommandClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.trade.config.TradeProperties;
import com.tianji.trade.constants.TradeErrorInfo;
import com.tianji.trade.service.ICartService;
import com.tianji.trade.service.IOrderDetailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private CourseClient courseClient;
    @Mock
    private IOrderDetailService detailService;
    @Mock
    private ICartService cartService;
    @Mock
    private TradeProperties tradeProperties;
    @Mock
    private RabbitMqHelper rabbitMqHelper;
    @Mock
    private PromotionClient promotionClient;
    @Mock
    private PromotionCommandClient promotionCommandClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void shouldRejectWhenAnyRequestedCourseDoesNotExist() {
        long existingId = 1L;
        long missingId = 10023L;
        when(courseClient.getSimpleInfoList(List.of(existingId, missingId)))
                .thenReturn(List.of(purchasableCourse(existingId)));

        assertThatThrownBy(() -> orderService.prePlaceOrder(List.of(existingId, missingId)))
                .isInstanceOf(BizIllegalException.class)
                .hasMessage(TradeErrorInfo.COURSE_NOT_EXISTS);
    }

    @Test
    void shouldNormalizeDuplicateCourseIdsForPreOrder() {
        long courseId = 1L;
        when(courseClient.getSimpleInfoList(List.of(courseId)))
                .thenReturn(List.of(purchasableCourse(courseId)));
        when(promotionClient.findDiscountSolution(anyList())).thenReturn(List.of());

        var result = orderService.prePlaceOrder(List.of(courseId, courseId));

        assertThat(result.getCourses()).hasSize(1);
        assertThat(result.getCourses().get(0).getId()).isEqualTo(courseId);
        verify(courseClient).getSimpleInfoList(List.of(courseId));
    }

    @Test
    void shouldRejectUnavailableCourseDuringPreOrder() {
        long courseId = 1L;
        CourseSimpleInfoDTO course = purchasableCourse(courseId);
        course.setStatus(CourseStatus.DOWN_SHELF.getValue());
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(course));

        assertThatThrownBy(() -> orderService.prePlaceOrder(List.of(courseId)))
                .isInstanceOf(BizIllegalException.class)
                .hasMessage(TradeErrorInfo.COURSE_NOT_FOR_SALE);
    }

    @Test
    void shouldRejectMixedInvalidCourseIdsWithoutCallingCourseService() {
        assertThatThrownBy(() -> orderService.prePlaceOrder(java.util.Arrays.asList(1L, null)))
                .isInstanceOf(BizIllegalException.class)
                .hasMessage(TradeErrorInfo.COURSE_NOT_EXISTS);

        verifyNoInteractions(courseClient);
    }

    private CourseSimpleInfoDTO purchasableCourse(long courseId) {
        CourseSimpleInfoDTO course = new CourseSimpleInfoDTO();
        course.setId(courseId);
        course.setName("Course " + courseId);
        course.setPrice(19900);
        course.setStatus(CourseStatus.SHELF.getValue());
        course.setThirdCateId(3L);
        course.setPurchaseEndTime(LocalDateTime.now().plusDays(1));
        return course;
    }
}
