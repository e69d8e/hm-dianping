package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//        // 从 redis 查询缓存
//        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.CACHE_SHOP_KEY + id);
//        // 存在返回
//        if (!entries.isEmpty()) {
//            // 检查是否为空值标记
//            if (entries.size() == 1 && entries.containsKey("_empty_")) {
//                return Result.fail("店铺不存在");
//            }
//            return Result.ok(BeanUtil.fillBeanWithMap(entries, new Shop(), false));
//        }
//        Shop shop = null;
//        String key = RedisConstants.LOCK_SHOP_KEY + id;
//        try {
//            boolean flag = tryLock(key);
//            if (!flag) { // 获取锁失败
//                Thread.sleep(50); // 休眠 50ms
//                return queryById(id); // 递归重试
//            }
//            // 不存在 查询数据库 写入 Redis 返回
//            shop = this.getById(id);
//            // 模拟耗时
////            Thread.sleep(500);
//            if (shop == null) {
//                // 解决缓存穿透 当数据库中没有时 设置空缓存
//                // 使用特殊标记表示空值
//                Map<String, String> emptyMap = new HashMap<>();
//                emptyMap.put("_empty_", "true");
//                stringRedisTemplate.opsForHash().putAll(RedisConstants.CACHE_SHOP_KEY + id, emptyMap);
//                // 设置过期时间 + 随机随机事件 减少 缓存雪崩问题
//                stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, (long) (RedisConstants.CACHE_NULL_TTL + Math.random() * 10), TimeUnit.MINUTES);
//                return Result.fail("店铺不存在");
//            }
//            stringRedisTemplate.opsForHash().putAll(RedisConstants.CACHE_SHOP_KEY + id,
//                    BeanUtil.beanToMap(shop, new HashMap<>(),
//                            CopyOptions.create().ignoreNullValue().setFieldValueEditor(
//                                    (fileName, value) -> value == null ? null : value.toString())));
//            // 设置有效期
//            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            unLock(key);
//        }
//        return Result.ok(shop);
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
