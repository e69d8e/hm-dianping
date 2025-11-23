package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Data
public class SimpleRedisLock implements ILock { // 不可重入锁
    // 锁的key
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString().replace("-", "") + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        // 脚本初始化
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

//    @Override
//    public void unLock() {
//        // 获取线程标示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 判断锁中的线程标示是否一致
//        if (!threadId.equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX + name))) {
//            // 在判断锁中的线程标示是否一致后 如果发生阻塞 等到时间锁自动释放后 可能会删除其他线程的锁 导致数据不一致 可使用 lua 脚本解决
//            return;
//        }
//        stringRedisTemplate.delete(KEY_PREFIX + name);
//    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}
