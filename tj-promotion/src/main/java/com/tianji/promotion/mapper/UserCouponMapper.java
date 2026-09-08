package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.enums.UserCouponStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author Sy
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * Atomically mark an owned, currently valid coupon as used.
     */
    @Update("""
            UPDATE user_coupon
            SET status = 2, used_time = #{usedTime}
            WHERE id = #{userCouponId}
              AND user_id = #{userId}
              AND status = 1
              AND term_begin_time IS NOT NULL
              AND term_begin_time <= #{now}
              AND term_end_time IS NOT NULL
              AND term_end_time >= #{now}
            """)
    int writeOff(@Param("userCouponId") Long userCouponId,
                 @Param("userId") Long userId,
                 @Param("now") LocalDateTime now,
                 @Param("usedTime") LocalDateTime usedTime);

    /**
     * Atomically refund an owned, currently used coupon. Expired coupons remain expired.
     */
    @Update("""
            UPDATE user_coupon
            SET status = CASE WHEN term_end_time IS NOT NULL AND term_end_time < #{now} THEN 3 ELSE 1 END,
                used_time = NULL
            WHERE id = #{userCouponId}
              AND user_id = #{userId}
              AND status = 2
            """)
    int refund(@Param("userCouponId") Long userCouponId,
               @Param("userId") Long userId,
               @Param("now") LocalDateTime now);

    List<Coupon> queryMyCoupons(@Param("userId") Long userId);

    List<Coupon> queryCouponByUserCouponIds(
            @Param("userCouponIds") List<Long> userCouponIds,
            @Param("userId") Long userId,
            @Param("status") UserCouponStatus status);
}
