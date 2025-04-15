package com.sport.domain;

public class CommonResult<T> {
    private int code; // 状态码
    private String message; // 消息
    private T data; // 数据

    // 私有化构造函数，防止外部直接创建对象
    private CommonResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 成功方法：只返回数据
    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(200, "操作成功", data);
    }

    // 成功方法：自定义消息和数据
    public static <T> CommonResult<T> success(String message, T data) {
        return new CommonResult<>(200, message, data);
    }

    // 失败方法：只返回错误消息
    public static <T> CommonResult<T> failure(String message) {
        return new CommonResult<>(500, message, null);
    }

    // 认证失败
    public static <T> CommonResult<T> authFailure() {
        return new CommonResult<>(401, "认证信息失效，请重新授权", null);
    }


    // 失败方法：自定义错误码和消息
    public static <T> CommonResult<T> failure(int code, String message) {
        return new CommonResult<>(code, message, null);
    }

    // Getters and Setters
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ResponseResult{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
