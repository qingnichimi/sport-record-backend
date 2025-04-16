package com.sport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sport.constants.RedisKeyConstant;
import com.sport.domain.Activity;
import com.sport.exception.AuthenticationFailedException;
import com.sport.vo.AccessTokenInfoVO;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class StravaService {
    private static final Logger log = LoggerFactory.getLogger(StravaService.class);
    @Value("${strava.client-id}")
    private String clientId;

    @Value("${strava.client-secret}")
    private String clientSecret;

    @Value("${strava.redirect-uri}")
    private String redirectUri;

    @Value("${strava.oauth-token-url}")
    private String oauthTokenUrl;

    @Value("${strava.api-url}")
    private String apiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    private static final long REFRESH_THRESHOLD = 300; // 提前5分钟刷新

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccessTokenInfoVO getAccessToken(String authorizationCode) throws JsonProcessingException {
        // 构建表单数据
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", authorizationCode);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", redirectUri);

        // 创建请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        // 发送 POST 请求
        ResponseEntity<AccessTokenInfoVO> response =
            restTemplate.exchange(oauthTokenUrl, HttpMethod.POST, entity, AccessTokenInfoVO.class);
        // 获取 access_token
        AccessTokenInfoVO responseBody = response.getBody();
        AccessTokenInfoVO accessTokenInfoVO = new AccessTokenInfoVO();
        if (responseBody != null) {
            redisTemplate.opsForValue().set(RedisKeyConstant.ACCESS_INFO, responseBody);
            accessTokenInfoVO.setRefreshToken(responseBody.getRefreshToken());
            accessTokenInfoVO.setAccessToken(responseBody.getAccessToken());
            accessTokenInfoVO.setExpiresAt(responseBody.getExpiresAt());
        }
        return accessTokenInfoVO;
    }

    public AccessTokenInfoVO refreshAccessToken(String refreshToken) throws JsonProcessingException {
        // 构建表单数据
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<AccessTokenInfoVO> response =
            restTemplate.exchange(oauthTokenUrl, HttpMethod.POST, entity, AccessTokenInfoVO.class);
        // 获取 access_token
        AccessTokenInfoVO responseBody = response.getBody();
        AccessTokenInfoVO accessTokenInfoVO = new AccessTokenInfoVO();
        if (responseBody != null) {
            redisTemplate.opsForValue()
                .set(RedisKeyConstant.ACCESS_INFO, responseBody);
            accessTokenInfoVO.setRefreshToken(responseBody.getRefreshToken());
            accessTokenInfoVO.setAccessToken(responseBody.getAccessToken());
            accessTokenInfoVO.setExpiresAt(responseBody.getExpiresAt());
        }
        return accessTokenInfoVO;
    }

    public List<Activity> getActivities(int page, int perPage, int before, int after) throws AuthenticationFailedException {

        List<Activity> activities = (List<Activity>)redisTemplate.opsForValue()
            .get(RedisKeyConstant.ACTIVITY_LIST + DateFormatUtils.format(new Date(), "yyyy-MM-dd"));
        if (activities != null && !activities.isEmpty()) {
            return activities;
        }
        AccessTokenInfoVO accessToken = (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken == null) {
            throw new AuthenticationFailedException("认证信息失效");
        }
        String url = apiUrl + "/athlete/activities";

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken.getAccessToken());

        // 构建请求参数
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", String.valueOf(page));
        params.add("per_page", String.valueOf(perPage));

        // 创建请求体
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        // 调用 Strava API
        ResponseEntity<List<Activity>> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Activity>>() {
            });

        // 返回活动列表
        List<Activity> activityList = response.getBody();
        redisTemplate.opsForValue()
            .set(RedisKeyConstant.ACTIVITY_LIST + DateFormatUtils.format(new Date(), "yyyy-MM-dd"), activityList,
                Duration.ofHours(24)); // 存24小时
        return activityList;
    }

    @Scheduled(fixedDelay = 12 * 60 * 60 * 1000)
    public void getActivitiesTask() {
        // 1. 从Redis获取AccessTokenDTO对象
        AccessTokenInfoVO accessToken = (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken == null) {
            log.error("用户未授权或登录已过期，请重新登录");
            return;
        }
        String url = apiUrl + "/athlete/activities";

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken.getAccessToken());

        // 构建请求参数
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", String.valueOf(1));
        params.add("per_page", String.valueOf(10));

        // 创建请求体
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        // 调用 Strava API
        ResponseEntity<List<Activity>> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Activity>>() {
            });

        // 返回活动列表
        List<Activity> activityList = response.getBody();
        redisTemplate.opsForValue()
            .set(RedisKeyConstant.ACTIVITY_LIST + DateFormatUtils.format(new Date(), "yyyy-MM-dd"), activityList,
                Duration.ofHours(24)); // 存24小时
    }

    // 存储Token
    public void storeToken(AccessTokenInfoVO tokenInfo) {
        String key = RedisKeyConstant.ACCESS_INFO;
        redisTemplate.opsForValue()
            .set(key, tokenInfo, tokenInfo.getExpiresIn() - REFRESH_THRESHOLD, // 设置比实际过期时间短的Redis过期
                TimeUnit.SECONDS);
    }

    // 每5分钟检查一次
    @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void refreshTokens() {
        log.info("开始检查Token过期情况...");
        AccessTokenInfoVO accessToken = (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken != null) {
            // 检查是否即将过期(剩余时间小于阈值)
            long remainingTime = accessToken.getExpiresAt() - System.currentTimeMillis()/1000;

            if (remainingTime < REFRESH_THRESHOLD) {
                log.info("Token即将过期，开始刷新...");
                try {
                    // 使用refreshToken获取新Token
                    AccessTokenInfoVO newToken = refreshAccessToken(accessToken.getRefreshToken());

                    // 更新存储
                    storeToken(newToken);
                    log.info("Token刷新成功");
                } catch (Exception e) {
                    log.error("Token刷新失败: {}", e.getMessage());
                }
            }
        }
    }

}
