package com.sport.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sport.domain.Activity;
import com.sport.domain.CommonResult;
import com.sport.service.StravaService;
import com.sport.vo.AccessTokenInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/strava")
public class StravaController {

    @Autowired
    private StravaService stravaService;

    @PostMapping("/getAccessToken")
    public CommonResult getAccessToken(@RequestParam String code) throws JsonProcessingException {
        AccessTokenInfoVO accessTokenInfo = stravaService.getAccessToken(code);
        if (accessTokenInfo != null) {
            return CommonResult.success("授权成功");
        } else {
            return CommonResult.authFailure();
        }
    }

    @PostMapping("/refreshToken")
    public CommonResult refreshAccessToken(@RequestParam("refreshToken") String refreshToken) throws JsonProcessingException {
        AccessTokenInfoVO newAccessToken = stravaService.refreshAccessToken(refreshToken);

        if (newAccessToken != null) {
            return CommonResult.success("刷新成功");
        } else {
            return CommonResult.authFailure();
        }
    }

    @GetMapping("/athlete/activity/list")
    public CommonResult getActivities(@RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "999") int pageSize) {
        List<Activity> list = stravaService.getActivities(pageNum, pageSize, 0, 0);
        return CommonResult.success(list);
    }
}
