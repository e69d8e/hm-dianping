package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RequiredArgsConstructor
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWork redisIdWork;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // 查询
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        VoucherOrder byId = getById(voucherId);
        // 判断优惠券时间
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        // 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

//        // synchronized 锁对象 但是在多台服务器中会导致锁对象不同 从而一个用户可以秒杀多次
//        synchronized (userId.toString().intern()) {
//            // 拿到Transactional代理对象 直接调用方法会导致事物失效
//            IVoucherOrderService proxyT = (IVoucherOrderService) AopContext.currentProxy();
//            return proxyT.createOrder(voucherId);
//        }

        // 创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate); // 不可重入锁
        RLock lock = redissonClient.getLock("lock:order:" + userId); // 可重入锁
        // 获取锁
//        boolean isLock = lock.tryLock(5L);
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        // 判断获取锁是否成功
        if (!isLock) {
            // 获取锁失败
            return Result.fail("请勿重复下单");
        }
        try {
            // 拿到Transactional代理对象 直接调用方法会导致事物失效
            IVoucherOrderService proxyT = (IVoucherOrderService) AopContext.currentProxy();
            return proxyT.createOrder(voucherId);
        } catch (Exception e) {
            return Result.fail("服务器异常");
        } finally {
            // 释放锁
//            lock.unlock();
            lock.unlock();
        }
    }

    @Transactional
    public Result createOrder(Long voucherId) {

        // 一人一单 查询订单是否存在
        int count = query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("不能重复下单");
        }
        // 扣减库存
        boolean success = seckillVoucherService
                .update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        // 创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWork.nextId("order");
        order.setId(orderId);
        order.setUserId(UserHolder.getUser().getId());
        order.setVoucherId(voucherId);
        save(order);
        return Result.ok(orderId);
    }
}
