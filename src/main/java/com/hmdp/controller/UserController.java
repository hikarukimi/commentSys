package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.sql.Time;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author hikarukimi
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone) {
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid){
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().
                set(RedisConstants.LOGIN_CODE_KEY + phone,code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("手机号{}发送验证码成功，验证码为：{}", phone, code);
        return Result.ok("验证码已发送有效期为"+ RedisConstants.LOGIN_CODE_TTL + "分钟");
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
        if (phoneInvalid){
            return Result.fail("手机号格式错误");
        }
        String codeKey=RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone();
        String code=stringRedisTemplate.opsForValue().get(codeKey);
        if (code==null||!code.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        stringRedisTemplate.delete(codeKey);
        User one = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, loginForm.getPhone()));
        if (one==null){
            User user = new User();
            user.setPhone(loginForm.getPhone());
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(5));
            userService.save(user);
            one=user;
        } else if (one.getPassword().equals(PasswordEncoder.encode(loginForm.getPassword()))){
            return Result.fail("密码错误");
        }
        String tokenKey=RedisConstants.LOGIN_USER_KEY+one.getId();
        stringRedisTemplate.opsForHash().
                putAll(tokenKey, BeanUtil.beanToMap(one,new HashMap<>(),CopyOptions.create().setFieldValueEditor(
                        (key, value) -> value.toString()
                )));
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(one.getId().toString());
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
