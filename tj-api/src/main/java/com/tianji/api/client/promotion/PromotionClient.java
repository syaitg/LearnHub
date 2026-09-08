package com.tianji.api.client.promotion;

import com.tianji.api.client.promotion.fallback.PromotionClientFallback;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 促销服务查询接口。
 *
 * <p>该客户端只承载优惠信息查询，查询失败时可以安全使用空结果降级。
 * 优惠券核销和退还属于有状态写操作，使用 {@link PromotionCommandClient}，不配置降级。</p>
 */
@FeignClient(value = "promotion-service", fallbackFactory = PromotionClientFallback.class)
public interface PromotionClient {

    /**
     * 查询订单可用的优惠方案。
     *
     * @param orderCourses 订单课程信息
     * @return 优惠方案列表
     */
    @PostMapping("/user-coupons/available")
    List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourses);

    /**
     * 查询订单优惠明细。
     *
     * @param orderCouponDTO 订单和优惠券信息
     * @return 优惠明细，不适用时返回 {@code null}
     */
    @PostMapping("/user-coupons/discount")
    CouponDiscountDTO queryDiscountDetailByOrder(@RequestBody OrderCouponDTO orderCouponDTO);

    /**
     * 查询优惠券使用规则。
     *
     * @param userCouponIds 用户优惠券 ID 集合
     * @return 优惠规则列表
     */
    @GetMapping("/user-coupons/rules")
    List<String> queryDiscountRules(@RequestParam("couponIds") List<Long> userCouponIds);
}
