package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RedisIdWork { // redis id 生成器

    private static final long BEGIN_TIMESTAMP = 1735689600L;
    private final StringRedisTemplate stringRedisTemplate;
    private static final long COUNT_BITS = 32;

    public Long nextId(String key) {
        // 获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTimestamp - BEGIN_TIMESTAMP;
        // 生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + key + ":" + date);
        // 拼接并返回
        return timeStamp << COUNT_BITS | count;
    }
}
