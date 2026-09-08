package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collections;
import java.util.List;

/**
 * 促销查询客户端降级处理。
 *
 * <p>仅为无副作用的查询接口提供空结果降级，不处理优惠券核销和退还等写操作。
 * 写操作客户端不配置 fallback，避免远程调用失败后被误判为执行成功。</p>
 */
@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    /**
     * 创建促销查询客户端降级实例。
     *
     * @param cause 远程调用失败原因
     * @return 促销查询客户端降级实例
     */
    @Override
    public PromotionClient create(Throwable cause) {
        log.error("查询促销服务出现异常，将使用空查询结果降级", cause);
        return new PromotionClient() {
            /**
             * 降级查询订单可用的优惠方案。
             *
             * @param orderCourses 订单课程信息
             * @return 空优惠方案列表
             */
            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
                return Collections.emptyList();
            }

            /**
             * 降级查询订单优惠明细。
             *
             * @param orderCouponDTO 订单和优惠券信息
             * @return {@code null}，表示暂时无法计算优惠明细
             */
            @Override
            public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
                return null;
            }

            /**
             * 降级查询优惠券使用规则。
             *
             * @param userCouponIds 用户优惠券 ID 集合
             * @return 空优惠规则列表
             */
            @Override
            public List<String> queryDiscountRules(List<Long> userCouponIds) {
                return Collections.emptyList();
            }
        };
    }
}
