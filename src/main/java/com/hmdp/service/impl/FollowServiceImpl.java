package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate redisTemplate;
    private final IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        if (UserHolder.getUser() == null) {
            return Result.fail("用户未登录");
        }
        // 判断是否已经关注了该用户
        Long userId = UserHolder.getUser().getId();
        Follow follow = query().eq("user_id", userId).eq("follow_user_id", followUserId).one();
        String key = RedisConstants.FOLLOWS_KEY + userId;
        if (isFollow) {
            if (follow == null) {
                follow = new Follow();
                follow.setUserId(userId);
                follow.setFollowUserId(followUserId);
                save(follow);
                // 存到 Redis 中
                redisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            if (follow != null) {
                removeById(follow.getId());
                redisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("用户未登录");
        }
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return count > 0 ? Result.ok(true) : Result.ok(false);
    }

    @Override
    public Result followCommons(Long id) {
        if (UserHolder.getUser() == null) {
            return Result.fail("用户未登录");
        }
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_KEY + userId;
        Set<String> common = redisTemplate.opsForSet().intersect(key, RedisConstants.FOLLOWS_KEY + id);
        if (common == null || common.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析id
        List<Long> ids = common.stream().map(Long::parseLong).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
