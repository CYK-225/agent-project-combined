package com.cyk.common;

import java.io.Serializable;

/**
 * 统一 API 响应结果封装类
 * @param <T> 数据载体类型
 */
public class ResultData<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 状态码（200表示成功，500表示失败等）
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 实际返回的数据载体
     */
    private T data;

    // 私有化构造函数，强制使用静态工厂方法
    private ResultData() {}

    private ResultData(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ============================
    // 成功响应的静态工厂方法
    // ============================

    public static <T> ResultData<T> success() {
        return new ResultData<>(200, "操作成功", null);
    }

    public static <T> ResultData<T> success(T data) {
        return new ResultData<>(200, "操作成功", data);
    }

    public static <T> ResultData<T> success(String message, T data) {
        return new ResultData<>(200, message, data);
    }

    // ============================
    // 失败响应的静态工厂方法
    // ============================

    public static <T> ResultData<T> error() {
        return new ResultData<>(500, "操作失败", null);
    }

    public static <T> ResultData<T> error(String message) {
        return new ResultData<>(500, message, null);
    }

    public static <T> ResultData<T> error(Integer code, String message) {
        return new ResultData<>(code, message, null);
    }

    // ============================
    // Getter 和 Setter (确保 JSON 序列化正常工作)
    // ============================

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
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
}