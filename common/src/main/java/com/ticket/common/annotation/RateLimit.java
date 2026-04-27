package com.ticket.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** 限流类型 */
    LimitType type();

    /** 时间窗口内最大请求数（BLACKLIST 类型无需设置） */
    int limit() default 100;

    /** 时间窗口大小（秒，BLACKLIST 类型无需设置） */
    int window() default 60;

    /** 触发限流时的提示信息，空则使用默认错误码消息 */
    String message() default "";
}
