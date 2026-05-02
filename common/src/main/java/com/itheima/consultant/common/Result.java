package com.itheima.consultant.common;

/**
 * 统一API响应结果封装
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 * @Description 统一结果处理
 */
public class Result<T> {
    /**
     * 状态码
     */
    private String status;

    /**
     * 获取状态码
     */
    public String getStatus() {
        return status;
    }

    /**
     * 状态信息，错误描述
     */
    private String message;

    /**
     * 获取消息内容
     */
    public String getMessage() {
        return message;
    }

    /**
     * 响应数据
     */
    private T data;

    /**
     * 获取响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 无参构造函数，供 Jackson 反序列化使用
     */
    protected Result() {
    }

    private Result(String status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    private Result(String status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 构建带有状态、消息和数据的结果对象
     *
     * @param status  状态
     * @param message 消息内容
     * @param data    数据
     * @return 结果对象
     */
    public static <T> Result<T> buildResult(Status status, String message, T data) {
        return new Result<T>(status.getCode(), message, data);
    }

    /**
     * 构建带有状态和消息的结果对象
     *
     * @param status  状态
     * @param message 消息内容
     * @return 结果对象
     */
    public static <T> Result<T> buildResult(Status status, String message) {
        return new Result<T>(status.getCode(), message);
    }

    /**
     * 构建带有状态和数据的结果对象
     *
     * @param status 状态
     * @param data   数据
     * @return 结果对象
     */
    public static <T> Result<T> buildResult(Status status, T data) {
        return new Result<T>(status.getCode(), status.getReason(), data);
    }

    /**
     * 构建只有状态的结果对象
     *
     * @param status 状态
     * @return 结果对象
     */
    public static <T> Result<T> buildResult(Status status) {
        return new Result<T>(status.getCode(), status.getReason());
    }

    /**
     * 响应状态枚举
     */
    public enum Status {
        /**成功*/
        SUCCESS("200", "正确"),
        /**系统错误*/
        SYSTEM_ERROR("101", "系统异常"),
        /**SQL错误*/
        SQL_ERROR("109", "SQL语句异常"),
        /**错误请求*/
        BAD_REQUEST("400", "错误的请求"),
        /**未认证*/
        UNAUTHORIZED("401", "禁止访问"),
        /**未找到*/
        NOT_FOUND("404", "没有可用的数据"),
        /**密码错误*/
        PWD_ERROR("300", "密码错误"),
        /**已存在*/
        EXIT("403", "已经存在"),
        /**服务器内部错误*/
        INTERNAL_SERVER_ERROR("500", "服务器遇到了一个未曾预料的状况"),
        /**服务不可用*/
        SERVICE_UNAVAILABLE("503", "服务器当前无法处理请求"),
        /**数据为空*/
        ERROR("9999", "数据不能为空"),
        /**用户不存在*/
        UNKNOWNUSER("123","用户不存在");

        /**
         * 状态码
         */
        private final String code;
        /**
         * 状态描述
         */
        private final String reason;

        Status(String code, String reason) {
            this.code = code;
            this.reason = reason;
        }

        public String getCode() {
            return code;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return code + ": " + reason;
        }
    }
}
