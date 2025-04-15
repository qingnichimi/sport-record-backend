package com.sport.config;

import com.sport.domain.CommonResult;
import com.sport.exception.AuthenticationFailedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    public CommonResult handleAuthFailed(AuthenticationFailedException ex) {
        return CommonResult.failure(ex.getCode(), ex.getMessage());
    }
}
