package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWork redisIdWork;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        // 脚本初始化
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 查询
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher == null) {
//            return Result.fail("优惠券不存在");
//        }
//        VoucherOrder byId = getById(voucherId);
//        // 判断优惠券时间
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束");
//        }
//        // 判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
////        // synchronized 锁对象 但是在多台服务器中会导致锁对象不同 从而一个用户可以秒杀多次
////        synchronized (userId.toString().intern()) {
////            // 拿到Transactional代理对象 直接调用方法会导致事物失效
////            IVoucherOrderService proxyT = (IVoucherOrderService) AopContext.currentProxy();
////            return proxyT.createOrder(voucherId);
////        }
//
//        // 创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate); // 不可重入锁
//        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId); // 可重入锁
//        // 获取锁
////        boolean isLock = lock.tryLock(5L);
//        boolean isLock = lock.tryLock();
//        // 判断获取锁是否成功
//        if (!isLock) {
//            // 获取锁失败
//            return Result.fail("请勿重复下单");
//        }
//        try {
//            // 拿到Transactional代理对象 直接调用方法会导致事物失效
//            IVoucherOrderService proxyT = (IVoucherOrderService) AopContext.currentProxy();
//            return proxyT.createOrder(voucherId);
//        } catch (Exception e) {
//            return Result.fail("服务器异常");
//        } finally {
//            // 释放锁

    /// /            lock.unlock();
//            lock.unlock();
//        }
//    }

    // 添加ApplicationRunner依赖
    private final ApplicationEventPublisher eventPublisher;

    // 添加销毁方法
    @PreDestroy
    public void destroy() {
        // 停止后台线程
        if (createVoucherOrder != null) {
            createVoucherOrder.stop();
        }
        // 关闭线程池
        SECKILL_ORDER_EXECUTOR.shutdown();
        try {
            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                SECKILL_ORDER_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SECKILL_ORDER_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private createVoucherOrder createVoucherOrder;

    @PostConstruct // 执行时机：创建对象之后
    public void init() {
        createVoucherOrder = new createVoucherOrder();
        SECKILL_ORDER_EXECUTOR.submit(createVoucherOrder);
    }

    @PreDestroy
    public void preDestroy() {
        // 停止消费线程
        if (createVoucherOrder != null) {
            createVoucherOrder.stop();
        }

        // 给线程一些时间来完成当前任务
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    //    // 阻塞队列
//    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 获取阻塞队列中的消息并处理
//    private class createVoucherOrder implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 获取队列中的订单信息
//                    VoucherOrder order = orderTasks.take();
//                    // 创建订单
//                    handlerVoucherOrder(order);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }

    // 处理 redis 消息队列中的消息并处理
    private class createVoucherOrder implements Runnable {
        String queueName = "stream.orders";
        private volatile boolean shouldRun = true;

        // 添加停止方法
        public void stop() {
            this.shouldRun = false;
        }

        @Override
        public void run() {
            while (shouldRun) {
                try {
                    // 获取队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 判断获取消息成功与否
                    if (list == null || list.isEmpty()) {
                        // 失败 说明没有消息
                        continue;
                    }
                    // 成功 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 创建订单
                    createOrder1(voucherOrder);
                    // 确认消息
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (IllegalStateException e) {
                    // 应用关闭时的正常现象，直接退出循环
                    log.info("Redis connection closed, stopping consumer thread");
                    break;
                } catch (Exception e) {
                    if (!shouldRun) {
                        // 应用正在关闭，忽略异常
                        break;
                    }
                    log.error("处理订单异常", e);
                    // 未确认的消息 会添加到 pending list 中
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (shouldRun) {
                try {
                    // 获取 pending list 队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 判断获取消息成功与否
                    if (list == null || list.isEmpty()) {
                        // 失败 说明 pending list 没有消息
                        break;
                    }
                    // 成功 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 创建订单
                    createOrder1(voucherOrder);
                    // 确认消息
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (IllegalStateException e) {
                    // 应用关闭时的正常现象
                    log.info("Redis connection closed during pending list handling");
                    break;
                } catch (Exception e) {
                    if (!shouldRun) {
                        // 应用正在关闭，忽略异常
                        break;
                    }
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder order) {
        Long userId = order.getUserId();
        // 创建锁对象
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            log.error("获取锁失败");
            return;
        }
        try {
            createOrder1(order);
        } catch (Exception e) {
            log.error("处理订单异常", e);
        } finally {
            // 释放锁
            lock.unlock();
        }

    }

    private IVoucherOrderService proxyT;

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        // 执行 lua 脚本
//        Long res = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString()
//        );
//        int r = res.intValue();
//        if (r != 0) {
//            return Result.fail(r == 1 ? "库存不足" : "请勿重复下单");
//        }
//        // 生成订单id
//        Long orderId = redisIdWork.nextId("order");
//        // 创建订单
//        VoucherOrder order = new VoucherOrder();
//        order.setId(orderId);
//        order.setUserId(userId);
//        order.setVoucherId(voucherId);
//        // 放入阻塞队列 异步执行
//        orderTasks.add(order);
//        // 获取Transactional代理对象
//        this.proxyT = (IVoucherOrderService) AopContext.currentProxy();
//        return Result.ok(orderId);
//    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long id = redisIdWork.nextId("order");
        // 执行lua脚本
        Long res = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(id)
        );
        int r = res.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "请勿重复下单");
        }
        return Result.ok(id);
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

    @Override
    public void createOrder1(VoucherOrder order) {
        Long voucherId = order.getVoucherId();
        // 一人一单 查询订单是否存在
        int count = query().eq("user_id", order.getUserId()).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("不能重复下单");
            return;
        }
        // 扣减库存
        boolean success = seckillVoucherService
                .update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        // 创建订单
        save(order);
    }
}
