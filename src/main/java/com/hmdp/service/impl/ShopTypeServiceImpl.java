package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        // 从 redis 中查询缓存
        List<String> typeList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        if (typeList != null && !typeList.isEmpty()) {
            return Result.ok(typeList.stream().map(item -> JSONUtil.toBean(item, ShopType.class)));
        }
        // 缓存不存在 从数据库中查询
        List<ShopType> shopTypes = this.query().orderByAsc("sort").list();
        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        // 缓存到 redis
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY,
                shopTypes.stream().map(JSONUtil::toJsonStr).toArray(String[]::new) );
        // 设置有效期
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypes);
    }
}
