package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    @Override
    public Result queryById(Long id) {
        // 从 redis 查询缓存
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.CACHE_SHOP_KEY + id);
        // 存在返回
        if (!entries.isEmpty()) {
            return Result.ok(BeanUtil.fillBeanWithMap(entries, new Shop(), false));
        }
        // 不存在 查询数据库 写入 Redis 返回
        Shop shop = this.getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForHash().putAll(RedisConstants.CACHE_SHOP_KEY + id,
                BeanUtil.beanToMap(shop, new HashMap<>(),
                        CopyOptions.create().ignoreNullValue().setFieldValueEditor(
                                (fileName, value) -> value == null ? null : value.toString())));
        // 设置有效期
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

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
