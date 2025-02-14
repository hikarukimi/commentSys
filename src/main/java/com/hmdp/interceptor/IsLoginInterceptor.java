package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器，用于检查用户是否已登录。
 * 如果用户未登录，则拦截请求并返回401状态码。
 * 如果用户已登录，则刷新用户的登录状态并允许请求继续。
 *
 * @author Hikarukimi
 */
public class IsLoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IsLoginInterceptor.class);

    static {
        LoggerFactory.getLogger(IsLoginInterceptor.class);
    }

    // 用于操作Redis的模板对象
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造函数，注入StringRedisTemplate。
     *
     * @param stringRedisTemplate Redis操作模板
     */
    public IsLoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 在处理请求之前进行调用（Controller方法调用之前）。
     * 检查请求头中的授权信息，验证用户是否已登录。
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param handler  处理器对象
     * @return 如果用户已登录，则返回true，继续处理请求；如果未登录，则返回false，不再继续处理。
     * @throws Exception 可能抛出的异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        // 从Redis中获取用户信息
        User user = BeanUtil.toBean(stringRedisTemplate.opsForHash().
                entries(RedisConstants.LOGIN_USER_KEY + token), User.class);
        if (user.getId() == null){
            // 用户未登录，设置响应状态码为401
            response.setStatus(401);
            response.getWriter().write("{\"success\":false,\"msg\":\"未登录\"}");
            return false;
        }
        // 保存用户信息到ThreadLocal，以便在请求处理过程中使用
        UserHolder.saveUser(UserDTO.entityToDto(user));
        // 刷新用户登录状态的过期时间
        stringRedisTemplate
                .expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    /**
     * 请求处理之后进行调用，但在视图被渲染之前（Controller方法调用之后）。
     * 清除ThreadLocal中的用户信息，防止内存泄漏。
     *
     * @param request  请求对象
     * @param response 响应对象
     * @param handler  处理器对象
     * @param modelAndView 视图对象
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        UserHolder.removeUser();
    }
}

