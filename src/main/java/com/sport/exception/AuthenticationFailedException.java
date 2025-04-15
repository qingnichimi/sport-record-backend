package com.sport.exception;

public class AuthenticationFailedException extends RuntimeException {

    private int code;

    public AuthenticationFailedException(String message) {
        super(message);
        this.code = 401; // 可自定义错误码
    }

    public AuthenticationFailedException(String message, int code) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
