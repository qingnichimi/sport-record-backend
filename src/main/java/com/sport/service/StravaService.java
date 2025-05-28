package com.sport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sport.constants.RedisKeyConstant;
import com.sport.domain.Activity;
import com.sport.exception.AuthenticationFailedException;
import com.sport.utils.RedisUtils;
import com.sport.vo.AccessTokenInfoVO;
import com.sport.vo.ActivityStatisticsVO;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.time.DateUtils;
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

import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

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
    @Autowired
    private RedisUtils redisUtils;

    @PostConstruct
    public void init() {
        log.info("项目启动，执行一次全量活动数据拉取...");
//        getAllActivitiesTask();
    }

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
        if (responseBody != null) {
            redisTemplate.opsForValue().set(RedisKeyConstant.ACCESS_INFO, responseBody);
        }
        return responseBody;
    }

    public List<Activity> getActivities(int page, int perPage, int before, int after)
        throws AuthenticationFailedException {
        AccessTokenInfoVO accessToken =
            (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken == null) {
            throw new AuthenticationFailedException("认证信息失效");
        }
        List<Activity> activities = redisUtils.getList(RedisKeyConstant.ACTIVITY_LIST,page, perPage, Activity.class);
        if (activities != null && !activities.isEmpty()) {
            return activities;
        }
        String url = apiUrl + "/athlete/activities?page =" + 1 + " &per_page=50";

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken.getAccessToken());

        // 创建请求体
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(headers);

        // 调用 Strava API
        ResponseEntity<List<Activity>> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Activity>>() {
            });

        // 返回活动列表
        List<Activity> activityList = response.getBody();
        activityList = activityList.stream().sorted(Comparator.comparing(Activity::getStartDateLocal)).collect(Collectors.toList());
        redisUtils.pushListWithDedup(RedisKeyConstant.ACTIVITY_LIST, activityList, null, "id");
        return redisUtils.getList(RedisKeyConstant.ACTIVITY_LIST,page, perPage, Activity.class);
    }

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void getActivitiesTask() {
        log.info("开始更新活动数据...");
        // 1. 从Redis获取AccessTokenDTO对象
        AccessTokenInfoVO accessToken =
            (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken == null) {
            log.error("用户未授权或登录已过期，请重新登录");
            return;
        }
        // 获取今天 0 点 和 明天 0 点（单位是秒）
        long after = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long before = LocalDate.now().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        String url = apiUrl + "/athlete/activities?" + "before=" + before + "&per_page=30";

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken.getAccessToken());

        // 创建请求体
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(headers);

        // 调用 Strava API
        ResponseEntity<List<Activity>> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Activity>>() {
            });

        // 返回活动列表
        List<Activity> activityList = response.getBody();
        activityList = activityList.stream().sorted(Comparator.comparing(Activity::getStartDateLocal)).collect(Collectors.toList());
        redisUtils.pushListWithDedup(RedisKeyConstant.ACTIVITY_LIST, activityList, null, "id");
        log.info("完成活动数据更新...");
    }

    public void getAllActivitiesTask() {
        if (redisTemplate.hasKey(RedisKeyConstant.ACTIVITY_LIST)) {
            log.info("活动数据已存在，跳过全量更新");
            return;
        }
        log.info("开始全量更新活动数据...");

        AccessTokenInfoVO accessToken =
            (AccessTokenInfoVO) redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken == null) {
            log.error("用户未授权或登录已过期，请重新登录");
            return;
        }

        String urlBase = apiUrl + "/athlete/activities";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken.getAccessToken());
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(headers);

        int page = 1;
        int perPage = 200;
        List<Activity> allActivities = new ArrayList<>();

        while (true) {
            String url = urlBase + "?per_page=" + perPage + "&page=" + page;
            ResponseEntity<List<Activity>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Activity>>() {}
            );

            List<Activity> activityList = response.getBody();
            if (activityList == null || activityList.isEmpty()) {
                break; // 没有更多数据了
            }

            allActivities.addAll(activityList);
            log.info("已获取第 {} 页，共 {} 条", page, activityList.size());
            page++;

            // 为避免触发 rate limit，可适当 sleep
            try {
                Thread.sleep(500); // 0.5 秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 按时间排序 + 去重 + 存入 Redis
        allActivities = allActivities.stream()
            .sorted(Comparator.comparing(Activity::getStartDateLocal))
            .collect(Collectors.toList());

        redisUtils.pushListWithDedup(RedisKeyConstant.ACTIVITY_LIST, allActivities, null, "id");

        log.info("完成 {} 条活动数据的全量更新。", allActivities.size());
    }

    public void storeToken(AccessTokenInfoVO tokenInfo) {
        String key = RedisKeyConstant.ACCESS_INFO;
        redisTemplate.opsForValue().set(key, tokenInfo);
    }

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void refreshTokens() {
        AccessTokenInfoVO accessToken =
            (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken != null) {
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
        log.info("检查Token完成...");
    }

    public ActivityStatisticsVO getActivityStatistics() {
        List<Activity> activities = redisUtils.getList(RedisKeyConstant.ACTIVITY_LIST, 1, 999, Activity.class);
        if (activities == null || activities.isEmpty()) {
            return new ActivityStatisticsVO(0,0,0,0);
        }

        double currentYearDistance = activities.stream()
            .filter(a -> DateUtils.truncatedCompareTo(a.getStartDateLocal(), new Date(), Calendar.YEAR) == 0)
            .mapToDouble(Activity::getDistance)
            .sum() / 1000;
        // 城市统计
        long cityCount = activities.stream()
            .map(Activity::getCity)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        // 距离统计
        double totalDistance = activities.stream()
            .mapToDouble(Activity::getDistance)
            .sum() / 1000;
        return new ActivityStatisticsVO(activities.size(),totalDistance,currentYearDistance,cityCount);
    }
}
