package com.tianji.api.client.trade.fallback;

import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.dto.course.CoursePurchaseInfoDTO;
import com.tianji.api.dto.trade.OrderConfirmVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TradeClientFallback implements FallbackFactory<TradeClient> {

    @Override
    public TradeClient create(Throwable cause) {
        log.error("查询交易服务异常", cause);
        return new TradeClient() {

            @Override
            public Map<Long, Integer> countEnrollNumOfCourse(List<Long> courseIdList) {
                return new HashMap<>();
            }

            @Override
            public Map<Long, Integer> countEnrollCourseOfStudent(List<Long> studentIds) {
                return new HashMap<>();
            }

            @Override
            public Boolean checkMyLesson(Long id) {
                return false;
            }

            @Override
            public CoursePurchaseInfoDTO getPurchaseInfoOfCourse(Long courseId) {
                return new CoursePurchaseInfoDTO(0, 0, 0);
            }

            @Override
            public OrderConfirmVO prePlaceOrder(List<Long> courseIds) {
                // 预下单属于写操作前置确认，失败时必须显式返回空，禁止伪装成零元空订单。
                return null;
            }
        };
    }
}
