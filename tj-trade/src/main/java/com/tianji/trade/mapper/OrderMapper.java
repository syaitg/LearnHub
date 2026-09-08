package com.tianji.trade.mapper;

import com.tianji.trade.domain.po.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 订单 Mapper 接口
 * </p>
 *
 * @author Sy
 * @since 2026-08-29
 */
public interface OrderMapper extends BaseMapper<Order> {

    Order getById(Long id);
}
