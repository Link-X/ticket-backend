package com.ticket.common.annotation;

public enum LimitType {
    /** 全局限流：所有用户共享同一计数器，保护系统整体吞吐 */
    GLOBAL,
    /** 用户限流：按登录用户 ID 隔离，防止单用户刷接口 */
    USER,
    /** IP 限流：按客户端 IP 隔离，防止匿名刷接口 */
    IP,
    /** 黑名单：检查用户/IP 是否在黑名单中，在则直接拒绝 */
    BLACKLIST
}
