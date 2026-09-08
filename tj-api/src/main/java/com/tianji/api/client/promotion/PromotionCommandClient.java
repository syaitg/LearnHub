package com.tianji.api.client.promotion;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 促销服务有状态写操作接口。
 *
 * <p>优惠券核销和退还不能使用静默降级。远程服务不可用时直接保留调用失败，
 * 由当前业务事务或上层补偿机制处理，避免把未执行的写操作误判为成功。</p>
 */
@FeignClient(value = "promotion-service", contextId = "promotionCommandClient")
public interface PromotionCommandClient {

    /**
     * 核销用户优惠券。
     *
     * @param userCouponIds 用户优惠券 ID 集合
     */
    @PutMapping("/user-coupons/use")
    void writeOffCoupon(@RequestParam("couponIds") List<Long> userCouponIds);

    /**
     * 退还用户优惠券。
     *
     * @param userCouponIds 用户优惠券 ID 集合
     */
    @PutMapping("/user-coupons/refund")
    void refundCoupon(@RequestParam("couponIds") List<Long> userCouponIds);
}
