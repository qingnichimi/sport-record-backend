package com.sport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 创建一个转换器列表
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();

        // 添加支持 application/x-www-form-urlencoded 的 FormHttpMessageConverter
        messageConverters.add(new FormHttpMessageConverter());

        // 创建 ObjectMapper 并设置命名策略为 SNAKE_CASE
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // 使用自定义 ObjectMapper 创建 MappingJackson2HttpMessageConverter
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        messageConverters.add(jacksonConverter);

        // 返回配置后的 RestTemplate
        return new RestTemplate(messageConverters);
    }
}
