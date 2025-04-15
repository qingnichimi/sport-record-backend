package com.sport.controller;

import com.sport.domain.CommonResult;
import com.sport.service.DeepSeekService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training")
public class TrainingController {
    @Autowired
    private DeepSeekService deepSeekService;

    @GetMapping("/suggestion")
    public CommonResult<String> getTrainingSuggestion() {
        try {
         return CommonResult.success(deepSeekService.getDayAdvice());
        } catch (Exception e) {
            return CommonResult.failure("获取运动数据失败: " + e.getMessage());
        }
    }

}