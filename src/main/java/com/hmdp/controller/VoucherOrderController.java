package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SecKillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISecKillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderController.class);

    @Resource
    private ISecKillVoucherService secKillVoucherService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisLock redisLock;

    @Transactional
    @PostMapping("seckill/{id}")
    public Result secKillVoucher(@PathVariable("id") Long voucherId) {
        SecKillVoucher byId = secKillVoucherService.getById(voucherId);
        if (byId == null) {
            log.error("优惠券不存在,被查询的优惠卷id为{}", voucherId);
            return Result.fail("优惠券不存在");
        }
        if (byId.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = voucherOrderService.getOne(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, userId));
        if (order != null) {
            return Result.fail("不能重复购买");
        }

        String lockKey =voucherId + ":" + userId;
        try {
            boolean locked = redisLock.lock(lockKey, userId.toString());
            if (!locked) {
                return Result.fail("当前抢购人数过多，请稍后再试");
            }

            boolean update = secKillVoucherService.update(new LambdaUpdateWrapper<SecKillVoucher>()
                    .eq(SecKillVoucher::getStock, byId.getStock()).set(SecKillVoucher::getStock, byId.getStock() - 1));
            if (!update) {
                return Result.fail("购买失败");
            }

            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrderService.save(voucherOrder);

            return Result.ok(voucherOrder.getId());
        } finally {
            log.info("释放锁,key:{},value:{}", lockKey, userId.toString());
            redisLock.unlock(lockKey, userId.toString());
        }
    }
}

