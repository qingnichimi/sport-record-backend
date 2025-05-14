package com.sport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sport.constants.RedisKeyConstant;
import com.sport.domain.Activity;
import com.sport.utils.RedisUtils;
import com.sport.vo.AccessTokenInfoVO;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekService {
    @Autowired
    private StravaService stravaService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedisUtils redisUtils;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    public String getTrainingSuggestion() throws IOException {
        // 1. 从Redis获取AccessTokenDTO对象
        AccessTokenInfoVO accessToken = (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
        if (accessToken == null) {
            return "用户未授权或登录已过期，请重新登录";
        }

        // 2. 获取最近活动数据
        List<Activity> activities = redisUtils.getList(RedisKeyConstant.ACTIVITY_LIST,1, 1, Activity.class);
        if (activities == null || activities.isEmpty()) {
            return "未找到最近的跑步活动记录";
        }

        // 3. 构建更专业的提示词
        String prompt = buildTrainingPrompt(activities);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("temperature", 0.8);
        requestBody.put("stream", false); // 显式禁用流式

        List<Map<String, String>> messages = List.of(
            Map.of("role", "user", "content", prompt)
        );
        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // 发送请求
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, request, String.class);

        // 解析 response body
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());

        // 提取 content 内容
        String content = root.path("choices").get(0).path("message").path("content").asText();

        System.out.println("AI 回复内容: " + content);
        return content;
    }

    private String buildTrainingPrompt(List<Activity> activities) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的跑步教练，拥有运动科学硕士学位和10年执教经验。");
        prompt.append("请根据用户最近的跑步数据，给出专业的今日训练建议：\n\n");
        prompt.append("用户近期跑步数据：\n");

        // 创建日期格式化器
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        activities.forEach(activity -> {
            prompt.append(String.format("- %s: %s, 距离 %.2f公里, 时长 %s, 平均配速 %s/公里",
                dateFormat.format(activity.getStartDate()),
                activity.getName(),
                activity.getDistance() / 1000,
                formatDuration(activity.getElapsedTime()),
                formatPace(activity.getAverageSpeed())));

            // 心率数据可能为空
            if (activity.getAverageHeartrate() != null && activity.getAverageHeartrate() > 0) {
                prompt.append(String.format(", 平均心率 %s", activity.getAverageHeartrate()));
            }
            prompt.append("\n");
        });

        prompt.append("\n请给出包含以下内容的专业建议：\n");
        prompt.append("1. 近期训练表现分析\n");
        prompt.append("2. 今日具体训练计划（距离、配速、心率区间）\n");
        prompt.append("3. 热身和放松建议\n");
        prompt.append("4. 营养和恢复建议\n");
        prompt.append("5. 注意事项\n\n");
        prompt.append("请用中文回答，保持专业但友好的语气");

        return prompt.toString();
    }

    private String formatDuration(int seconds) {
        return String.format("%d小时%02d分钟", seconds / 3600, (seconds % 3600) / 60);
    }

    private String formatPace(double speed) {
        if (speed == 0) return "N/A";
        double paceSeconds = 1000 / speed;
        return String.format("%d分%02d秒", (int)paceSeconds / 60, (int)paceSeconds % 60);
    }

    @Scheduled(cron = "0 0 8 * * ?") // 每天早上8点执行
    public void generateDailyAdvice() {
        try {
            // 1. 从Redis获取AccessTokenDTO对象
            AccessTokenInfoVO accessToken = (AccessTokenInfoVO)redisTemplate.opsForValue().get(RedisKeyConstant.ACCESS_INFO);
            if (accessToken == null) {
                return;
            }
            String advice = getTrainingSuggestion();
            redisTemplate.opsForValue().set(RedisKeyConstant.DAY_ADVICE + DateFormatUtils.format(new Date(), "yyyy-MM-dd"), advice, Duration.ofHours(24)); // 存24小时
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getDayAdvice() {
        String res = (String)redisTemplate.opsForValue().get(RedisKeyConstant.DAY_ADVICE + DateFormatUtils.format(new Date(), "yyyy-MM-dd"));
        if (res == null) {
            generateDailyAdvice();
            res = (String)redisTemplate.opsForValue().get(RedisKeyConstant.DAY_ADVICE + DateFormatUtils.format(new Date(), "yyyy-MM-dd"));
        }
        return res;
    }
}