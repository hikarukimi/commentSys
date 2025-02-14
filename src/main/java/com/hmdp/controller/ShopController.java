package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.server.HttpServerRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author hikarukimi
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    /**
     * 避免每次调用时都创建新的COPY_OPTIONS实例
     */
    private static final CopyOptions COPY_OPTIONS = CopyOptions.create()
            .setFieldValueEditor((k, value) -> value != null ? value.toString() : null);

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @param request HTTP请求对象，用于获取请求者IP地址
     * @return Result对象，包含商铺详情数据或错误信息
     * 业务逻辑：
     * 1. 构建Redis缓存键，用于存储和查询商铺信息。
     * 2. 尝试从Redis缓存中获取商铺信息，使用Hash结构存储商铺对象的属性。
     * 3. 如果缓存中存在商铺信息，且商铺id不为空，则直接返回商铺信息，避免不必要的数据库查询。
     * 4. 如果缓存中商铺名称为"缓存穿透"，则认为是恶意查询或商铺确实不存在，返回错误信息。
     * 5. 如果缓存中不存在商铺信息，为了防止缓存击穿，使用互斥锁（mutex）。
     * 6. 尝试获取互斥锁，如果获取失败，则等待一段时间后重新查询。
     * 7. 如果获取互斥锁成功，则从数据库中查询商铺信息。
     * 8. 如果数据库中不存在该商铺，则设置一个特殊商铺信息到缓存中，并返回店铺不存在的错误信息。
     * 9. 如果数据库中存在该商铺，则将商铺信息存入缓存，并设置缓存过期时间。
     * 10. 释放互斥锁，确保其他请求可以获取锁进行缓存重建。
     * 11. 返回商铺信息。
     */

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id, HttpServletRequest request) {
        // 构建缓存键
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从缓存中获取商铺信息
        Shop shop = BeanUtil.toBean(stringRedisTemplate.opsForHash().entries(key), Shop.class);

        // 检查缓存中是否存在商铺信息
        if (shop.getId() == null) {
            // 检查是否为缓存穿透
            if ("缓存穿透".equals(shop.getName())) {
                return Result.fail("查询结果为空，请确认商铺ID是否正确");
            }

            // 构建互斥锁键
            String mutexKey = RedisConstants.LOCK_SHOP_KEY + id;
            // 尝试获取互斥锁
            Boolean getLock = stringRedisTemplate.opsForValue().setIfAbsent(mutexKey, "", 10, TimeUnit.SECONDS);

            // 获取锁失败，等待后重试
            if (Boolean.FALSE.equals(getLock)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error("缓存重建失败，线程中断", e);
                    throw new RuntimeException(e);
                }
                return queryShopById(id, request);
            }

            // 获取锁成功，从数据库查询商铺信息
            shop = shopService.getById(id);
            if (shop == null) {
                // 商铺不存在，设置特殊值到缓存
                shop = new Shop();
                shop.setName("缓存穿透");
                stringRedisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(shop, new HashMap<>(), COPY_OPTIONS));
                stringRedisTemplate.expire(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                log.info("设置缓存穿透,来源ip为{}", request.getRemoteAddr());
                return Result.fail("店铺不存在");
            }

            // 商铺存在，存入缓存并设置过期时间
            stringRedisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(shop, new HashMap<>(), COPY_OPTIONS));
            stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

            // 释放互斥锁
            stringRedisTemplate.delete(mutexKey);
        }

        // 返回商铺信息
        return Result.ok(shop);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据，包含需要更新的商铺信息
     * @return Result对象，包含操作结果的状态和信息
     * 注意：此方法会先更新数据库中的商铺信息，然后删除Redis中对应的缓存，以确保数据的一致性
     */

    @PutMapping
    @Transactional
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.updateById(shop);
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
