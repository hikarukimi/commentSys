package com.hmdp.controller;


import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.List;

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
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList;
        List<String> range = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        if (range==null||range.isEmpty()){
            typeList= typeService.query().orderByAsc("sort").list();
            typeList.forEach(type-> stringRedisTemplate.
                    opsForList().rightPush(RedisConstants.CACHE_SHOP_TYPE_KEY, JSON.toJSONString(type)));
            return Result.ok(typeList);
        }
        typeList= typeService.query().orderByAsc("sort").list();
        log.info("查询出shop-type:{}",typeList);
        return Result.ok(range);
    }
}
