package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        if (id == null) {
            return Result.fail("参数错误");
        }
        // 查询博客
        Blog blog = getById(id);
        // 查询用户
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        // 查询当前用户是否点过赞
        blog.setIsLike(isBlogLiked(blog));
        return Result.ok(blog);
    }

    private boolean isBlogLiked(Blog blog) {
        if (UserHolder.getUser() == null) {
            return false;
        }
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 判断用户是否点过赞
        return score != null;
    }

    @Override
    public Result likeBlog(Long id) {
        if (UserHolder.getUser() == null) {
            return Result.ok();
        }
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            // 更新数据库
            update().setSql("liked = liked - 1").eq("id", id).update();
            // 存在，则取消点赞
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        } else {
            // 更新数据库
            update().setSql("liked = liked + 1").eq("id", id).update();
            // 不存在，则点赞
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId), System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            blog.setIsLike(isBlogLiked(blog));
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 查询点赞时间排名前5的用户id
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = userIds.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户 这种方式查询会导致排序出错 不会按照 ids 的顺序来排序
//        List<User> users = userService.listByIds(ids);

        String idStr = StringUtil.join(ids, ",");
        List<User> users = userService.query().in("id", ids)
                .last("order by field(id, " + idStr + ")").list();
        List<UserDTO> userDTOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
}
