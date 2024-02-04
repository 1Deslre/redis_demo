package com.hmdp.controller;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {

        String key = CACHE_SHOP_TYPE_KEY;
        // 1.查询redis缓存
        List<String> typeJson = stringRedisTemplate.opsForList().range(key, 0, -1);
        // 2.判断是否命中
        if (CollectionUtil.isNotEmpty(typeJson)) {
            // 2.0 如果为空对象(防止缓存穿透时存入的空对象)
            assert typeJson != null;
            if (StrUtil.isBlank(typeJson.get(0))) {
                return Result.fail("商品分类信息为空！");
            }
            // 2.1 命中则转换List<String> -> List<ShopType> 并返回、
            List<ShopType> typeList = new ArrayList<>();
            for (String jsonString : typeJson) {
                ShopType shopType = JSONUtil.toBean(jsonString, ShopType.class);
                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        // 3. 未命中，查询数据库
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        // 3.1 数据库中不存在
        if (CollectionUtil.isEmpty(typeList)) {
            // 添加空对象到redis，解决缓存穿透
            stringRedisTemplate.opsForList().rightPushAll(key, CollectionUtil.newArrayList(""));
            stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误
            return Result.fail("商品分类信息为空！");
        }
        // 3.2 数据库中存在,转换List<ShopType> -> List<String> 类型
        List<String> shopTypeList = new ArrayList<>();
        for (ShopType shopType : typeList) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypeList.add(jsonStr);
        }
        // 4.写入redis缓存, 有顺序只能RPUSH
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);
        // 5. 返回
        return Result.ok(typeList);

//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
//        return Result.ok(typeList);
    }
}
