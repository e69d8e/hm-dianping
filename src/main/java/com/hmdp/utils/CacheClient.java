package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(value, new HashMap<>(),
                CopyOptions.create()
                        .ignoreNullValue()
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString())));
        stringRedisTemplate.expire(key, (long) (time + Math.random() * 10), unit);
    }

    public <T, ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        // 获取缓存数据
        String key = keyPrefix + id;
        Map<Object, Object> value = stringRedisTemplate.opsForHash().entries(key);
        if (!value.isEmpty()) {
            // 检查是否为空值标记
            if (value.size() == 1 && value.containsKey("_empty_")) {
                return null;
            }
            return BeanUtil.toBean(value, type);
        }
        T t = null;
        try {
            boolean flag = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (!flag) {
                // 获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithPassThrough(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 缓存重建
            t = dbFallback.apply(id);
            if (t == null) {
                Map<String, String> emptyMap = new HashMap<>();
                emptyMap.put("_empty_", "true");
                stringRedisTemplate.opsForHash().putAll(keyPrefix + id, emptyMap);
                stringRedisTemplate.expire(keyPrefix + id, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key, t, time, unit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        return t;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
