package com.sport.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RedisUtils {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUtils(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // 存多个对象（右推入），按 ID 去重
    public <T> void pushListWithDedup(String key, List<T> dataList, Duration expire, String idField) {
        try {
            for (T data : dataList) {
                // 获取活动的 ID
                String activityId = getActivityId(data, idField);

                // 检查该活动 ID 是否已经存在于 Set 中
                boolean exists = redisTemplate.opsForSet().isMember(key + ":ids", activityId);
                if (!exists) {
                    // 如果不存在，则推入列表，并将 ID 存入 Set 中
                    String jsonData = objectMapper.writeValueAsString(data);
                    redisTemplate.opsForList().leftPush(key, jsonData);
                    redisTemplate.opsForSet().add(key + ":ids", activityId);
                }
            }

            // 设置过期时间
            if (expire != null) {
                redisTemplate.expire(key, expire);
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis pushListWithDedup error", e);
        }
    }

    // 获取活动 ID，假设活动对象有一个 getId() 方法
    private <T> String getActivityId(T data, String idField) throws JsonProcessingException {
        // 这里我们使用反射或 Jackson 获取指定字段的值
        // 假设每个活动都有一个 `idField` 字段
        return objectMapper.readTree(objectMapper.writeValueAsString(data)).get(idField).asText();
    }

    // 分页查询列表
    public <T> List<T> getList(String key, int page, int size, Class<T> clazz) {
        try {
            int start = (page - 1) * size;
            int end = start + size - 1;
            List<String> jsonList = redisTemplate.opsForList().range(key, start, end);
            if (jsonList == null) return Collections.emptyList();

            return jsonList.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, clazz);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Redis getList error", e);
        }
    }

    // 获取列表总数
    public long getSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }

    // 清空 key
    public void delete(String key) {
        redisTemplate.delete(key);
        redisTemplate.delete(key + ":ids"); // 同时清除活动 ID Set
    }
}
