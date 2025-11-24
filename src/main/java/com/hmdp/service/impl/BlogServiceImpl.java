package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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
    private final IFollowService followService;

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

    @Override
    public Result queryBlogByUserId(Long id, Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店笔记到数据库
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("发布笔记失败");
        }
        // 查询作者的所有粉丝
        List<Follow> followUsers = followService.query().eq("follow_user_id", user.getId()).list();
        if (followUsers == null || followUsers.isEmpty()) {
            return Result.ok(blog.getId());
        }
        // 将笔记id推送到粉丝的收件箱
        for (Follow follow : followUsers) {
            Long userId = follow.getUserId();
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        // 查询用户收件箱中的笔记id
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 获取 id
        List<String> strings = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).collect(Collectors.toList());
        // 获取 score
        List<Double> scores = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getScore).collect(Collectors.toList());
        // 解析 id
        List<Long> ids = strings.stream().map(Long::valueOf).collect(Collectors.toList());

        // 根据笔记id查询笔记信息
        String idStr = StringUtil.join(ids, ",");
//       不能用 listByIds(ids) 查询 这种方式查询会导致排序出错 不会按照 ids 的顺序来排序
        List<Blog> blogs = query().in("id", ids)
                .last("order by field(id, " + idStr + ")").list();
        ScrollResult<Blog> scrollResult = getBlogScrollResult(blogs, scores);

        return Result.ok(scrollResult);
    }

    private ScrollResult<Blog> getBlogScrollResult(List<Blog> blogs, List<Double> scores) {
        ScrollResult<Blog> scrollResult = new ScrollResult<>();
        // 判断该笔记是否点过赞 以及该笔记的用户信息
        for (Blog blog : blogs) {
            blog.setIsLike(isBlogLiked(blog));
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
        scrollResult.setList(blogs);
        // 计算 offset
        int newOffset = 1;
        double tmp = 0;
        for (int i = scores.size() - 1; i >= 0; i--) {
            if (i != scores.size() - 1 && tmp != scores.get(i)) {
                break;
            }
            if (i == scores.size() - 1) {
                tmp = scores.get(i);
                continue;
            }
            if (tmp == scores.get(i)) {
                newOffset += 1;
            }
        }
        scrollResult.setOffset(newOffset);
        scrollResult.setMinTime(scores.get(scores.size() - 1).longValue());
        return scrollResult;
    }
}
