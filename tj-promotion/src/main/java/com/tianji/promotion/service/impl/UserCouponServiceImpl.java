package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_RANGE_KEY;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author Sy
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService codeService;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;
    private static final RedisScript<String> EXCHANGE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
        EXCHANGE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/exchange_coupon.lua"), String.class);
    }

    @Override
    // @Lock(name = "lock:coupon:#{couponId}")
    public void receiveCoupon(Long couponId) {
        /*// 1.查询优惠券
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2.校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 3.校验库存
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();
        // 4.校验每人限领数量
        // 4.1.查询领取数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        // 4.2.校验限领数量
        if(count > coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量");
        }
        // 5.扣减优惠券库存
        redisTemplate.opsForHash().increment(
                PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum", -1);
*/
        // 1.执行LUA脚本，判断结果
        // 1.1.准备参数
        String key1 = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        String key2 = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long userId = UserContext.getUser();
        // 1.2.执行脚本
        Long r = redisTemplate.execute(RECEIVE_COUPON_SCRIPT, List.of(key1, key2), userId.toString());
        int result = NumberUtils.null2Zero(r).intValue();
        if (result != 0) {
            // 结果大于0，说明出现异常
            throw new BizIllegalException(PromotionConstants.RECEIVE_COUPON_ERROR_MSG[result - 1]);
        }
        // 2.发送MQ消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
    }

    private Coupon queryCouponByCache(Long couponId) {
        // 1.准备KEY
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 2.查询
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (objMap.isEmpty()) {
            return null;
        }
        // 3.数据反序列化
        return BeanUtils.mapToBean(objMap, Coupon.class, false, CopyOptions.create());
    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在！");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足！");
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, uc.getUserId());

        // 4.更新兑换码状态
        if (uc.getSerialNum() != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, uc.getUserId())
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, uc.getSerialNum())
                    .update();
        }
    }

    @Override
    // @Lock(name = "lock:coupon:#{T(com.tianji.common.utils.UserContext).getUser()}")
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.执行LUA脚本
        Long userId = UserContext.getUser();
        String result = redisTemplate.execute(
                EXCHANGE_COUPON_SCRIPT,
                List.of(COUPON_CODE_MAP_KEY, COUPON_RANGE_KEY),
                String.valueOf(serialNum), String.valueOf(serialNum + 5000), userId.toString());
        long r = NumberUtils.parseLong(result);
        if (r < 10) {
            // 异常结果应该是在1~5之间
            throw new BizIllegalException(PromotionConstants.EXCHANGE_COUPON_ERROR_MSG[(int) (r - 1)]);
        }
        // 3.发送MQ消息通知
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(r);
        uc.setSerialNum((int) serialNum);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
        /*// 2.校验是否已经兑换 SETBIT KEY 4 1
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已经被兑换过了");
        }
        try {
            // 3.查询兑换码对应的优惠券id
            Long couponId = codeService.exchangeTargetId(serialNum);
            if (couponId == null) {
                throw new BizIllegalException("兑换码不存在！");
            }
            Coupon coupon = couponMapper.selectById(couponId);
            // 4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())) {
                throw new BizIllegalException("优惠券活动未开始或已经结束");
            }

            // 5.校验每人限领数量
            Long userId = UserContext.getUser();
            // 5.1.查询领取数量
            String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
            Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
            // 5.2.校验限领数量
            if(count > coupon.getUserLimit()){
                throw new BadRequestException("超出领取数量");
            }

            // 6.发送MQ消息通知
            UserCouponDTO uc = new UserCouponDTO();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setSerialNum((int) serialNum);
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
        } catch (Exception e) {
            // 重置兑换的标记 0
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }*/
    }

    @Override
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.分页查询当前用户持有的优惠券，防止越权访问。
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage(OrderItem.asc("term_end_time")));
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.获取优惠券详细信息
        // 3.1.获取用户券关联的优惠券id
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        // 3.2.查询
        List<Coupon> coupons = couponMapper.selectByIds(couponIds);

        // 4.按用户券记录原顺序封装 VO，避免同一优惠券模板丢失用户券维度
        Map<Long, Coupon> couponMap = coupons.stream()
                .collect(Collectors.toMap(Coupon::getId, coupon -> coupon, (first, ignored) -> first));
        List<CouponVO> list = new ArrayList<>(records.size());
        for (UserCoupon record : records) {
            Coupon coupon = couponMap.get(record.getCouponId());
            if (coupon == null) {
                // 理论上不应出现，避免因异常数据返回一个错误券信息
                continue;
            }
            CouponVO vo = BeanUtils.toBean(
                    coupon, CouponVO.class, CopyOptions.create().setIgnoreProperties("status"));
            vo.setUserCouponId(record.getId());
            vo.setTermBeginTime(record.getTermBeginTime());
            vo.setTermEndTime(record.getTermEndTime());
            vo.setStatus(record.getStatus());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    @Override
    @Transactional
    public void writeOffCoupon(List<Long> userCouponIds) {
        List<Long> ids = normalizeCouponIds(userCouponIds);
        if (ids.isEmpty()) {
            return;
        }
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BizIllegalException("用户未登录");
        }

        // 只处理当前用户持有的优惠券，防止越权核销。
        List<UserCoupon> userCoupons = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .in(UserCoupon::getId, ids)
                .list();
        if (userCoupons.size() != ids.size()) {
            throw new BizIllegalException("优惠券不存在或不属于当前用户");
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Integer> usedNumDelta = new java.util.HashMap<>();
        for (UserCoupon userCoupon : userCoupons) {
            int updated = baseMapper.writeOff(userCoupon.getId(), userId, now, now);
            if (updated != 1) {
                throw new BizIllegalException("优惠券不可用，可能已使用、已过期或尚未生效");
            }
            usedNumDelta.merge(userCoupon.getCouponId(), 1, Integer::sum);
        }
        updateCouponUsedNum(usedNumDelta);
    }

    @Override
    @Transactional
    public void refundCoupon(List<Long> userCouponIds) {
        List<Long> ids = normalizeCouponIds(userCouponIds);
        if (ids.isEmpty()) {
            return;
        }
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BizIllegalException("用户未登录");
        }

        List<UserCoupon> userCoupons = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .in(UserCoupon::getId, ids)
                .list();
        if (userCoupons.size() != ids.size()) {
            throw new BizIllegalException("优惠券不存在或不属于当前用户");
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Integer> usedNumDelta = new java.util.HashMap<>();
        for (UserCoupon userCoupon : userCoupons) {
            if (UserCouponStatus.USED != userCoupon.getStatus()) {
                // 退款操作保持幂等：已退回或已过期的优惠券无需再次扣减使用数量。
                continue;
            }
            int updated = baseMapper.refund(userCoupon.getId(), userId, now);
            if (updated == 1) {
                usedNumDelta.merge(userCoupon.getCouponId(), -1, Integer::sum);
                continue;
            }

            // 并发请求或重试请求可能已经完成状态流转，再次查询确认最新状态。
            UserCoupon latest = lambdaQuery()
                    .eq(UserCoupon::getId, userCoupon.getId())
                    .eq(UserCoupon::getUserId, userId)
                    .one();
            if (latest != null && UserCouponStatus.USED != latest.getStatus()) {
                continue;
            }
            throw new DbException("优惠券退款失败，状态已发生变化");
        }
        updateCouponUsedNum(usedNumDelta);
    }

    private void updateCouponUsedNum(Map<Long, Integer> usedNumDelta) {
        for (Map.Entry<Long, Integer> entry : usedNumDelta.entrySet()) {
            int updated = couponMapper.updateUsedNum(entry.getKey(), entry.getValue());
            if (updated != 1) {
                throw new DbException("更新优惠券使用数量失败！");
            }
        }
    }

    private List<Long> normalizeCouponIds(List<Long> userCouponIds) {
        if (CollUtils.isEmpty(userCouponIds)) {
            return CollUtils.emptyList();
        }
        return userCouponIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> queryDiscountRules(List<Long> userCouponIds) {
        // 1.查询优惠券信息
        List<Coupon> coupons = baseMapper.queryCouponByUserCouponIds(userCouponIds, UserContext.getUser(), UserCouponStatus.USED);
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.转换规则
        return coupons.stream()
                .map(c -> DiscountStrategy.getDiscount(c.getDiscountType()).getRule(c))
                .collect(Collectors.toList());
    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }
}
