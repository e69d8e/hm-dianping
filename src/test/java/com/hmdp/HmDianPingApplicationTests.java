package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
@RunWith(SpringRunner.class)
@ComponentScan(basePackages = "com.hmdp")
public class HmDianPingApplicationTests {
    @Autowired
    private RedisIdWork redisIdWork;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IShopService shopService;

    @Test
    public void testRedisIdWork() {
        System.out.println(redisIdWork.nextId("order"));
    }

    @Test
    public void loadShopData() {
        // 查询店铺数据
        List<Shop> list = shopService.list();
        // 根据 type id 分组
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        map.forEach((typeId, shopList) -> {
            String key = "shop:geo:" + typeId;

//            // 方法一
//            for (Shop shop : shopList) {
//                stringRedisTemplate
//                        .opsForGeo()
//                        .add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
//            }

            // 方法二 批量添加
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
            for (Shop shop : shopList) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);

        });

    }

}
