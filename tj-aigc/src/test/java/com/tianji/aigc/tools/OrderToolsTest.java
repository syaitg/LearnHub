package com.tianji.aigc.tools;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.api.dto.trade.OrderConfirmVO;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderToolsTest {

    private static final String REQUEST_ID = "order-tool-test";

    @Mock
    private CourseClient courseClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private ToolContext toolContext;

    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        orderTools = new OrderTools(courseClient, tradeClient);
        lenient().when(toolContext.getContext()).thenReturn(Map.of("requestId", REQUEST_ID, "userId", 99L));
    }

    @AfterEach
    void tearDown() {
        ToolResultHolder.remove(REQUEST_ID);
        UserContext.removeUser();
    }

    @Test
    void shouldNotCallTradeForMissingCourseId() {
        when(courseClient.getSimpleInfoList(List.of(10023L))).thenReturn(List.of());

        assertThat(orderTools.prePlaceOrder(List.of(10023), toolContext)).isNull();

        verifyNoInteractions(tradeClient);
        assertThat(ToolResultHolder.get(REQUEST_ID)).isNull();
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void shouldNormalizeIdsAndStoreSuccessfulPreOrder() {
        long courseId = 1589905661084430337L;
        CourseSimpleInfoDTO course = purchasableCourse(courseId);
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(course));

        OrderCourseDTO orderCourse = new OrderCourseDTO().setId(courseId).setPrice(19900);
        OrderConfirmVO confirm = OrderConfirmVO.builder()
                .orderId(200L)
                .totalAmount(19900)
                .discounts(List.of())
                .courses(List.of(orderCourse))
                .build();
        when(tradeClient.prePlaceOrder(List.of(courseId))).thenReturn(confirm);

        var result = orderTools.prePlaceOrder(List.of(courseId, courseId), toolContext);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(200L);
        assertThat(result.getCourseIds()).containsExactly(courseId);
        assertThat(ToolResultHolder.get(REQUEST_ID, "prePlaceOrder")).isEqualTo(result);
        assertThat(UserContext.getUser()).isNull();
        verify(tradeClient).prePlaceOrder(List.of(courseId));
    }

    @Test
    void shouldMarkSingleFreeCourse() {
        long courseId = 2L;
        CourseSimpleInfoDTO course = purchasableCourse(courseId);
        course.setFree(true);
        course.setPrice(0);
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(course));

        OrderCourseDTO orderCourse = new OrderCourseDTO().setId(courseId).setPrice(0);
        OrderConfirmVO confirm = OrderConfirmVO.builder()
                .orderId(201L)
                .totalAmount(0)
                .discounts(List.of())
                .courses(List.of(orderCourse))
                .build();
        when(tradeClient.prePlaceOrder(List.of(courseId))).thenReturn(confirm);

        var result = orderTools.prePlaceOrder(List.of(courseId), toolContext);

        assertThat(result).isNotNull();
        assertThat(result.isFreeCourse()).isTrue();
    }

    @Test
    void shouldReturnNullWhenTradeFallbackReturnsNull() {
        long courseId = 1L;
        CourseSimpleInfoDTO course = purchasableCourse(courseId);
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(course));
        when(tradeClient.prePlaceOrder(List.of(courseId))).thenReturn(null);

        assertThat(orderTools.prePlaceOrder(List.of(courseId), toolContext)).isNull();
        assertThat(ToolResultHolder.get(REQUEST_ID)).isNull();
    }

    @Test
    void shouldNotCallTradeForUnavailableCourse() {
        long courseId = 1L;
        CourseSimpleInfoDTO course = purchasableCourse(courseId);
        course.setStatus(CourseStatus.DOWN_SHELF.getValue());
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(course));

        assertThat(orderTools.prePlaceOrder(List.of(courseId), toolContext)).isNull();

        verifyNoInteractions(tradeClient);
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void shouldRejectMixedInvalidCourseIdsWithoutRemoteCalls() {
        assertThat(orderTools.prePlaceOrder(List.of(1L, -1L), toolContext)).isNull();

        verifyNoInteractions(courseClient, tradeClient);
        assertThat(UserContext.getUser()).isNull();
    }

    private CourseSimpleInfoDTO purchasableCourse(long courseId) {
        CourseSimpleInfoDTO course = new CourseSimpleInfoDTO();
        course.setId(courseId);
        course.setStatus(CourseStatus.SHELF.getValue());
        course.setPurchaseEndTime(LocalDateTime.now().plusDays(1));
        return course;
    }

}
