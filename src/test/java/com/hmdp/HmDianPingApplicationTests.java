package com.hmdp;

import com.hmdp.utils.RedisIdWork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ComponentScan(basePackages = "com.hmdp")
public class HmDianPingApplicationTests {
    @Autowired
    private RedisIdWork redisIdWork;
    @Test
    public void testRedisIdWork() {
        System.out.println(redisIdWork.nextId("order"));
    }

}
