package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Collections;

/**
 * @author Hikarukimi
 */
@Component
public class RedisLock {

    private static final DefaultRedisScript<Long> LUA_SCRIPT;
    private static final String LOCK_PREFIX = "lock:";

    static {
        LUA_SCRIPT = new DefaultRedisScript<>();
        LUA_SCRIPT.setLocation(new ClassPathResource("redis_lock.lua"));
        LUA_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public RedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean lock(String key, String value) {
        String lockKey = LOCK_PREFIX + key;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, value, Duration.ofSeconds(300)));
    }

    public void unlock(String key, String value) {
        String lockKey = LOCK_PREFIX + key;
        stringRedisTemplate.execute(LUA_SCRIPT, Collections.singletonList(lockKey), value);
    }
}

