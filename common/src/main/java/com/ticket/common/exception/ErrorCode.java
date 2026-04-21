package com.ticket.common.exception;

/**
 * 错误码枚举.
 *
 * 定义系统级错误码(500/400/401 等)和业务级错误码(1001~1008).
 * 业务错误码以 1xxx 开头,与 HTTP 状态码区分.
 */
public enum ErrorCode {

    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),

    // --- 业务错误码 ---
    SEAT_NOT_AVAILABLE(1001, "座位不可用"),
    EXCEED_PURCHASE_LIMIT(1002, "超过限购数量"),
    TICKET_ALREADY_USED(1003, "票已使用"),
    TICKET_EXPIRED(1004, "票已过期"),
    ORDER_NOT_FOUND(1005, "订单不存在"),
    ORDER_EXPIRED(1006, "订单已过期"),
    SESSION_NOT_FOUND(1007, "场次不存在"),
    SHOW_NOT_FOUND(1008, "演出不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
