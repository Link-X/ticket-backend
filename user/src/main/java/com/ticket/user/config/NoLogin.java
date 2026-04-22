package com.ticket.user.config;

import java.lang.annotation.*;

/**
 * 标记免登录接口，加在方法或类上均有效。
 * 未加此注解的接口默认需要登录。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoLogin {
}
