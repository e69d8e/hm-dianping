package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        // 不符合 返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 符合 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码到 session
//        session.setAttribute("code", code);
        // 保存验证码到 redis

        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 模拟发送验证码
        log.info("发送验证码成功，验证码为：{}", code);
        // 返回结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        // 校验验证码 从 session 中获取
//        Object cacheCode = session.getAttribute("code");
//        String code = loginForm.getCode();
//        if (cacheCode == null || !cacheCode.toString().equals(code)) {
//            return Result.fail("验证码错误");
//        }

        // 判断用户是否存在 不存在 创建新用户保存到数据库
//        User user = lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
//        if (user == null) {
//            save(createUserWithPhone(loginForm.getPhone()));
//        }
        // 保存用户信息到 session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//        return Result.ok();
        // 校验验证码 从 redis 中获取
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        // 判断用户是否存在 不存在 创建新用户保存到数据库
        User user = lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
        if (user == null) {
            save(createUserWithPhone(loginForm.getPhone()));
        }
        // 生成一个随机的 token
        String token = UUID.randomUUID().toString();
        // 保存用户信息到 redis 中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // StringRedisSerializer 无法将 Long 类型数据序列化为 String 要将 Long 类型数据序列化为 String
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, BeanUtil.beanToMap(userDTO, new HashMap<>()
                , CopyOptions.create().ignoreNullValue().setFieldValueEditor((fileName, value) -> value == null ? "" : value.toString())
        ));
        // 设置有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 返回 token
        return Result.ok(token);
    }

    // 通过手机号创建新用户
    private User createUserWithPhone(String phone) {
        User newUser = new User();
        newUser.setPhone(phone);
        newUser.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        return newUser;
    }

    @Override
    public Result queryUserById(Long userId) {
        // 查询详情
        User user = getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        stringRedisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth()))
                        .valueAt(0) // 从第0位开始读取
        );
        log.info("result: {}", result);
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long tmp = result.get(0);
        if (tmp == null || tmp == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while (tmp != 0) {
            if ((tmp & 1) == 0) {
                break;
            }
            count++;
            // 无符号右移
            tmp = tmp >>> 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result logout(String authorization) {
        Boolean isLogout = stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + authorization);
        if (!isLogout) {
            return Result.fail("退出登录失败");
        }
        return Result.ok();
    }
}
